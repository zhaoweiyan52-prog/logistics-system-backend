param([string]$Base = 'http://localhost:8080/api')
$ErrorActionPreference = 'Stop'
function Assert-True($condition, [string]$message) {
    if (!$condition) { throw "验证失败：$message" }
}
function Call-Api([string]$method, [string]$path, $body, [string]$token) {
    $params = @{ Method=$method; Uri="$Base$path"; ContentType='application/json; charset=utf-8' }
    if ($token) { $params.Headers = @{ Authorization="Bearer $token" } }
    if ($null -ne $body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8)) }
    Invoke-RestMethod @params
}
$nameA = 'a' + [guid]::NewGuid().ToString('N').Substring(0,12)
$nameB = 'b' + [guid]::NewGuid().ToString('N').Substring(0,12)
$password = [guid]::NewGuid().ToString('N').Substring(0,16)
$a = Call-Api POST '/auth/register' @{username=$nameA;password=$password;role='customer'} ''
$b = Call-Api POST '/auth/register' @{username=$nameB;password=$password;role='customer'} ''
Assert-True ($a.code -eq 0 -and $b.code -eq 0) '注册应成功'
$login = Call-Api POST '/auth/login' @{username=$nameA;password=$password} ''
Assert-True ($login.code -eq 0) '登录应成功'
$o = Call-Api POST '/orders' @{receiver='测试同学';dest='上海市某大学';goods='图书';weight=1.5;fee=16} $a.data.token
Assert-True ($o.code -eq 0 -and $o.data.status -eq 'pending') '下单成功'
$no = $o.data.no
$detail = Call-Api GET "/orders/$no" $null $a.data.token
Assert-True ($detail.code -eq 0 -and $detail.data.receiver -eq '测试同学') '本人的订单详情'
$list = Call-Api GET '/orders?page=1&size=10' $null $a.data.token
Assert-True ($list.data.total -eq 1 -and $list.data.list[0].no -eq $no) '本人的列表'
$blocked = Call-Api GET "/orders/$no" $null $b.data.token
Assert-True ($blocked.code -eq 40401) '其他客户不能查看订单'
$empty = Call-Api GET '/orders' $null $b.data.token
Assert-True ($empty.data.total -eq 0) '其他客户列表应为空'
$wrong = Call-Api POST '/auth/login' @{username=$nameA;password='WrongPassword123'} ''
Assert-True ($wrong.code -eq 40101) '错误密码应拒绝'
$logout = Call-Api POST '/auth/logout' @{} $a.data.token
Assert-True ($logout.code -eq 0) '退出成功'
$unauthorized = $false
try { $null = Call-Api GET '/orders' $null $a.data.token }
catch { if ([int]$_.Exception.Response.StatusCode -eq 401) { $unauthorized=$true } else { throw } }
Assert-True $unauthorized '退出后的 token 必须返回 HTTP 401'
# 不输出密码、token；两个测试账号和一个订单会保留在当前数据库，供人工检查持久化。
[pscustomobject]@{ result='通过'; customerA=$nameA; customerB=$nameB; orderNo=$no; checks=10 } | ConvertTo-Json
