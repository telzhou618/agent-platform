package com.example.agent.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.example.agent.system.auth.LoginHelper;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 自动填充创建/更新时间；业务表自动填充创建人 userId */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (metaObject.hasSetter("userId")) {
            this.strictInsertFill(metaObject, "userId", Long.class, LoginHelper.currentUserId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
