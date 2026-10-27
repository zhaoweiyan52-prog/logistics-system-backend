package com.example.logistics.operations;

import com.example.logistics.api.BusinessException;
import com.example.logistics.auth.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.example.logistics.auth.Permissions.require;

@Service
public class WorkflowService {
    private final JdbcTemplate jdbc;
    public WorkflowService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static void page(int page,int size) {
        if(page<1 || size<1 || size>100) throw new BusinessException(40001,"page 从 1 开始，size 为 1-100");
    }
    public Map<String,Object> lockOrder(String no) {
        var rows=jdbc.queryForList("SELECT * FROM logistics_orders WHERE no=? FOR UPDATE",no);
        if(rows.isEmpty()) throw new BusinessException(40401,"运单不存在或无权查看");
        return rows.get(0);
    }
    public void newTask(String no,String type,String addr) {
        jdbc.update("INSERT INTO logistics_tasks(order_no,type,addr,created_at,status) VALUES (?,?,?,?,'todo')",
                no,type,addr,Timestamp.from(Instant.now()));
    }
    public void trace(String no,String text) {
        jdbc.update("INSERT INTO order_traces(order_no,event_time,txt,done) VALUES (?,?,?,true)",no,Timestamp.from(Instant.now()),text);
    }
    public void notice(long owner,String title,String desc,String act) {
        jdbc.update("INSERT INTO notifications(user_id,title,description,created_at,act) VALUES (?,?,?,?,?)",
                owner,title,desc,Timestamp.from(Instant.now()),act);
    }
    @Transactional
    public void sign(CurrentUser user,String no,OperationsModels.Sign r) {
        require(user,"customer");
        var o=lockOrder(no);
        if(((Number)o.get("owner_id")).longValue()!=user.id()) throw new BusinessException(40401,"运单不存在或无权查看");
        if(!"delivering".equals(o.get("status"))) throw new BusinessException(40902,"该运单当前不可签收");
        if(!r.code().equals(o.get("sign_code"))) throw new BusinessException(40003,"签收码不正确");
        jdbc.update("UPDATE logistics_orders SET status='delivered' WHERE no=?",no);
        jdbc.update("UPDATE logistics_tasks SET status='done' WHERE order_no=? AND status='todo'",no);
        trace(no,"已由"+(r.by()==null || r.by().isBlank()?"本人":r.by().trim())+"签收，感谢使用");
        if(settingFlag("sign_ntf")) notice(user.id(),"包裹已签收",no+" 已签收","track");
    }
    @Transactional
    public Map<String,Object> feedback(CurrentUser user,String no,OperationsModels.Feedback r) {
        require(user,"customer");
        var o=lockOrder(no);
        if(((Number)o.get("owner_id")).longValue()!=user.id()) throw new BusinessException(40401,"运单不存在或无权查看");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM order_feedback WHERE order_no=?",Long.class,no)>0)
            throw new BusinessException(40903,"该运单已提交过反馈");
        Instant now=Instant.now();
        jdbc.update("INSERT INTO order_feedback(order_no,type,content,created_at,reply) VALUES (?,?,?,?,'')",no,r.type().trim(),r.content().trim(),Timestamp.from(now));
        return Map.of("type",r.type().trim(),"content",r.content().trim(),"time",now,"reply","");
    }
    public Map<String,Object> tasks(CurrentUser user,String status,int page,int size) {
        require(user,"staff","admin"); page(page,size);
        if(!status.isEmpty() && !Set.of("todo","done").contains(status)) throw new BusinessException(40001,"任务状态不正确");
        String where=status.isEmpty()?"":" WHERE t.status=?";
        List<Object> args=new ArrayList<>(); if(!status.isEmpty()) args.add(status);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM logistics_tasks t"+where,Long.class,args.toArray());
        args.add(size); args.add(((long)page-1)*size);
        var list=jdbc.query("""
                SELECT t.*,o.status AS order_status,
                EXISTS(SELECT 1 FROM task_vouchers v WHERE v.task_id=t.id) AS voucher
                FROM logistics_tasks t JOIN logistics_orders o ON o.no=t.order_no
                """+where+" ORDER BY t.created_at DESC,t.id DESC LIMIT ? OFFSET ?",(rs,n)->Map.of(
                "id",rs.getLong("id"),"no",rs.getString("order_no"),"type",rs.getString("type"),"addr",rs.getString("addr"),
                "time",rs.getTimestamp("created_at").toInstant(),"status",rs.getString("status"),"voucher",rs.getBoolean("voucher"),"orderStatus",rs.getString("order_status")),args.toArray());
        return Map.of("list",list,"total",total);
    }
    private Map<String,Object> task(long id) {
        var rows=jdbc.queryForList("SELECT * FROM logistics_tasks WHERE id=?",id);
        if(rows.isEmpty()) throw new BusinessException(40402,"任务不存在");
        return rows.get(0);
    }
    @Transactional
    public void status(CurrentUser user,long id,OperationsModels.TaskStatus r) {
        require(user,"staff");
        var t=task(id); String no=(String)t.get("order_no"); var o=lockOrder(no);
        // 所有任务操作先锁同一运单，再重读任务，避免两个员工重复完成或逆序更新。
        // MySQL 默认 REPEATABLE READ 下普通 SELECT 会复用旧快照；锁定读才能看到前一员工刚提交的结果。
        t=jdbc.queryForMap("SELECT * FROM logistics_tasks WHERE id=? FOR UPDATE",id);
        if(!"todo".equals(t.get("status"))) throw new BusinessException(40904,"任务已完成，请刷新列表");
        String old=(String)o.get("status"), next=r.status();
        Set<String> allowed=switch(old) {
            case "pending" -> Set.of("transit","exception");
            case "transit" -> Set.of("delivering","exception");
            case "delivering" -> Set.of("delivered","exception");
            case "exception" -> Set.of("transit","delivering");
            default -> Set.of();
        };
        if(!allowed.contains(next)) throw new BusinessException(40905,"不允许从当前状态变更到所选状态");
        jdbc.update("UPDATE logistics_tasks SET status='done',operator_id=? WHERE id=?",user.id(),id);
        jdbc.update("UPDATE logistics_orders SET status=? WHERE no=?",next,no);
        String text=Map.of("transit","已揽收，运输中","delivering","快递员正在派送","delivered","员工确认已签收","exception","运单异常").get(next);
        trace(no,text+(r.note()==null || r.note().isBlank()?"":" · "+r.note().trim()));
        if(!"delivered".equals(next)) newTask(no,"派送",(String)o.get("dest"));
        if(("delivered".equals(next)&&settingFlag("sign_ntf")) || ("exception".equals(next)&&settingFlag("exc_ntf")))
            notice(((Number)o.get("owner_id")).longValue(),text,no,"track");
    }
    @Transactional
    public void voucher(CurrentUser user,long id,List<MultipartFile> files,String note) throws IOException {
        require(user,"staff");
        if(files==null || files.isEmpty() || files.size()>3 || note.length()>500) throw new BusinessException(40001,"请上传 1-3 张图片，说明不超过 500 字");
        List<byte[]> data=new ArrayList<>();
        for(var file:files) {
            if(file.isEmpty() || file.getSize()>2*1024*1024) throw new BusinessException(40001,"每张图片须为 1 字节至 2 MB");
            byte[] bytes=file.getBytes();
            // 校验实际格式；不采信文件名和浏览器声明的 Content-Type，不接受 SVG 或任意脚本。
            boolean png=bytes.length>8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpg=bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
            if(!png && !jpg) throw new BusinessException(40001,"只支持 PNG/JPEG 图片");
            try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new BusinessException(40001,"图片无法读取");
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    if(reader.getWidth(0)>4096 || reader.getHeight(0)>4096) throw new BusinessException(40001,"图片长宽最多 4096 像素");
                    if(reader.read(0)==null) throw new BusinessException(40001,"图片内容不完整");
                } finally {reader.dispose();}
            } catch(IOException e) {throw new BusinessException(40001,"图片内容损坏或不完整");}
            data.add(bytes);
        }
        var t=task(id); var o=lockOrder((String)t.get("order_no"));
        if(!"派送".equals(t.get("type"))) throw new BusinessException(40906,"请在派送任务上传签收凭证");
        if(!Set.of("delivering","delivered").contains(o.get("status"))) throw new BusinessException(40906,"仅派送中或已签收运单可上传签收凭证");
        if(!jdbc.queryForList("SELECT slot FROM task_vouchers WHERE task_id=? FOR UPDATE",id).isEmpty())
            throw new BusinessException(40907,"该任务已有凭证");
        for(int i=0;i<data.size();i++) jdbc.update("INSERT INTO task_vouchers(task_id,slot,content,note,uploaded_by,created_at) VALUES (?,?,?,?,?,?)",
                id,i,data.get(i),note.trim(),user.id(),Timestamp.from(Instant.now()));
        // 上传文件只证明凭证已保存，签收仍通过状态更新或客户签收接口完成。
    }
    private boolean settingFlag(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT "+column+" FROM system_settings WHERE id=1",Boolean.class));
    }
}
