package com.example.logistics.auth;

import com.example.logistics.api.BusinessException;
import java.util.Arrays;

public final class Permissions {
    private Permissions() {}
    public static void require(CurrentUser user, String... roles) {
        if (Arrays.stream(roles).noneMatch(user.role()::equals))
            throw new BusinessException(40301, "当前角色无权执行此操作");
    }
}
