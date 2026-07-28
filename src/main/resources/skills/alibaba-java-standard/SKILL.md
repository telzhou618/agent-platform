---
name: alibaba-java-standard
description: 在生成Java代码和SQL语句时，严格遵守阿里巴巴Java编程规范。包括命名规范、代码风格、异常处理、集合使用、并发安全、日志规范和SQL编写等核心要求。当需要编写或生成Java代码、MyBatis SQL、数据库脚本时，务必使用本规范确保代码质量、可维护性和安全性。
compatibility: ""
---

# 阿里Java编程规范 Skill

当生成Java代码或SQL语句时，遵循本规范确保代码质量。

## 1. Java命名规范

### 包名
- 使用**小写字母**，禁用大写字母
- 模式：`com.{company}.{project}.{module}` 
- 示例：`com.yoka.act.lottery.service`

### 类名
- 使用 **UpperCamelCase**（大驼峰）
- 示例：`LotteryActivityService`、`UserSubmissionRecord`
- 异常类以 `Exception` 结尾：`BusinessException`
- 测试类以 `Test` 结尾：`LotteryActivityServiceTest`

### 方法名、参数名、成员变量
- 使用 **lowerCamelCase**（小驼峰）
- 示例：`getUserInfo()`、`activityId`、`isActive`

### 常量名
- 使用 **UPPER_SNAKE_CASE**（全大写下划线分隔）
- 示例：`MAX_RETRY_COUNT`、`DEFAULT_TIMEOUT_MS`
- 值不变的成员变量也用常量名风格

### Boolean变量
- 前缀使用 `is`、`has`、`can` 等
- 示例：`isActive`、`hasPermission`、`canSubmit`
- 不使用 `isNotXxx` 或 `isNoXxx` 的双重否定

## 2. 代码风格

### 注释规范

**文件头注释（可选）**：
```java
/**
 * 类描述信息，简明扼要说明用途
 *
 * @author 张三
 * @since 1.0.0
 */
```

**类和接口注释**：
```java
/**
 * 抽奖活动服务类，负责抽奖活动的创建、更新、查询和执行逻辑。
 */
public class LotteryActivityService {
    ...
}
```

**public方法注释**：
```java
/**
 * 执行抽奖，返回中奖用户列表
 *
 * @param activityId 活动ID，非空
 * @param drawCount  抽奖数量
 * @return 中奖用户列表
 * @throws BusinessException 活动不存在或已结束
 */
public List<User> executeLottery(Long activityId, Integer drawCount) {
    ...
}
```

**特殊逻辑注释**：
- 仅注释 **why**（为什么）而非 **what**（做什么），代码应该自解释
- 示例：`// 按activity_id分片，避免跨分片全表扫描`
- 禁用无意义的注释：`i++; // i加1`

### 换行与长度限制
- 单行长度不超过 **120字符**
- 超过长度的语句分行，缩进 **4个空格**（使用空格而非Tab）
- 示例：
```java
List<String> items = repository.findByActivityIdAndStatus(
    activityId, ActivityStatus.ACTIVE);
```

### 空行规则
- 方法之间用 **1个空行** 分隔
- 逻辑块之间用 **1个空行** 分隔
- 类成员变量与方法之间用 **1个空行** 分隔

### 大括号位置
- 大括号不换行（Egyptian brackets）
```java
public void process() {
    if (condition) {
        doSomething();
    } else {
        doOtherThing();
    }
}
```

## 3. 异常处理

### 禁止行为
- ❌ 禁止吞异常（捕获后什么都不做）
- ❌ 禁止打印堆栈：`e.printStackTrace()`
- ❌ 禁止直接使用 `Exception` 或 `RuntimeException`

### 正确做法
```java
try {
    // 业务逻辑
} catch (SpecificException e) {
    log.error("操作失败，原因：[{}]", e.getMessage(), e);
    throw new BusinessException("用户操作失败", e);
}
```

### 异常分类
- 自定义业务异常：`BusinessException`
- 外部API异常：包装并记日志
- 系统异常：转换为业务异常或日志记录

## 4. 集合使用

### 返回值处理
- 禁止直接返回数据库查询结果，必须返回 **新的集合对象**（深拷贝）
```java
// ❌ 错误
public List<User> getUsers() {
    return userRepository.findAll();
}

// ✅ 正确
public List<User> getUsers() {
    List<User> result = new ArrayList<>(userRepository.findAll());
    return result;
}
```

### 集合初始化
- 使用带初始容量的构造器：`new ArrayList<>(16)`
- 避免多次扩容

### 遍历
```java
// ✅ 推荐：for-each循环
for (User user : users) {
    // 处理逻辑
}

// ✅ 推荐：stream（当有变换时）
List<String> names = users.stream()
    .map(User::getName)
    .collect(Collectors.toList());

// ❌ 禁止：Iterator且修改集合
for (Iterator<User> it = users.iterator(); it.hasNext();) {
    User user = it.next();
    if (needRemove(user)) {
        it.remove();  // 必须使用Iterator的remove
    }
}
```

## 5. 并发与线程安全

### 竞争写入
- 使用 **分布式锁**（Redisson）保护并发修改：
```java
RLock lock = redissonClient.getLock("activity:" + activityId);
try {
    lock.lock();
    // 临界区：更新活动状态
    updateActivityStatus(activityId, newStatus);
} finally {
    lock.unlock();
}
```

### 异步操作
- 重型操作（抽奖、积分更新）使用 **消息队列**（RocketMQ）
```java
lotteryMessage message = new LotteryMessage(activityId, userId);
rocketMQTemplate.convertAndSend(LOTTERY_TOPIC, message);
```

### 线程共享变量
- 禁止使用 `ThreadLocal` 除非有明确需求
- 使用 `volatile` 标记共享原始类型变量

## 6. 日志规范

### 日志级别使用
- **ERROR**：系统错误、异常、需要人工处理
- **WARN**：潜在风险、不符合预期但系统可继续运行
- **INFO**：重要业务节点（用户登录、支付成功、活动创建）
- **DEBUG**：开发调试信息，生产环境应禁用

### 日志格式
```java
// ✅ 正确
log.info("用户[{}]创建了活动[{}]，活动类型：{}", userId, activityId, activityType);

// ❌ 错误：字符串拼接，不使用参数占位符
log.info("用户" + userId + "创建了活动" + activityId);

// ❌ 错误：记录敏感信息
log.info("用户密码：{}", password);
```

### 敏感信息
- 禁止记录密码、令牌、身份证号等敏感信息
- 需要追踪时，仅记录摘要或masked版本

## 7. SQL编写规范（MyBatis）

### SQL风格
- **关键字大写**：SELECT、FROM、WHERE、LEFT JOIN、ORDER BY等
- **缩进清晰**：多行SQL使用缩进
```xml
<select id="findActiveActivities" resultType="Activity">
    SELECT id, name, type, status, created_at
    FROM sv_activity
    WHERE status = 'ACTIVE'
        AND created_at > #{startTime}
    ORDER BY created_at DESC
    LIMIT #{limit}
</select>
```

### 分片键在WHERE条件中
- 必须在WHERE子句中包含分片键，避免跨分片全表扫描
```xml
<!-- ✅ 正确：包含activity_id分片键 -->
<select id="findByActivityId">
    SELECT * FROM sv_user_submission_record
    WHERE activity_id = #{activityId}
        AND user_id = #{userId}
</select>

<!-- ❌ 错误：缺少activity_id，会全表扫描 -->
<select id="findByUserId">
    SELECT * FROM sv_user_submission_record
    WHERE user_id = #{userId}
</select>
```

### 分页规范
- 使用 **LIMIT** 分页，禁用 OFFSET 大值查询
```xml
<select id="queryByPage">
    SELECT * FROM sv_activity
    WHERE status = 'ACTIVE'
    ORDER BY created_at DESC
    LIMIT #{offset}, #{pageSize}
</select>
```

### 字段选择
- 指定具体字段，禁用 `SELECT *`
```xml
<!-- ❌ 错误 -->
<select id="getUser">
    SELECT * FROM sv_user
</select>

<!-- ✅ 正确 -->
<select id="getUser">
    SELECT id, username, email, created_at
    FROM sv_user
    WHERE id = #{id}
</select>
```

### JOIN与复杂查询
- 使用左外连接时，明确关联条件
- 避免笛卡尔积（JOIN条件不足）
```xml
<select id="findActivityWithCreator">
    SELECT a.id, a.name, u.username
    FROM sv_activity a
    LEFT JOIN sv_user u ON a.creator_id = u.id
    WHERE a.id = #{activityId}
</select>
```

### 批量操作
```xml
<!-- 批量插入 -->
<insert id="batchInsert">
    INSERT INTO sv_user_submission_record 
    (activity_id, user_id, submission_content, created_at)
    VALUES
    <foreach collection="records" item="record" separator=",">
        (#{record.activityId}, #{record.userId}, #{record.content}, NOW())
    </foreach>
</insert>
```

## 8. 数据库设计规范

### 表名与列名
- 使用 **snake_case**（下划线分隔）
- 示例：`sv_user`、`sv_activity`、`submission_content`
- 禁用驼峰或混合命名

### 主键与索引
- 每个表必须有 **唯一主键**（通常是自增ID）
- 频繁查询的字段建立索引
- 关联字段（外键）建立索引

### 数据类型
- 优先使用：BIGINT（long）、INT（int）、VARCHAR、DATETIME
- 避免 FLOAT/DOUBLE（精度问题），使用 DECIMAL
- 日期使用 DATETIME，不使用 DATE + TIME 分开

### 字段设计
```sql
-- ✅ 正确
CREATE TABLE sv_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '活动名称',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '活动状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP,
    creator_id BIGINT NOT NULL,
    KEY idx_creator_id (creator_id),
    KEY idx_status_created (status, created_at)
);
```

## 9. Entity/Model规范

```java
/**
 * 活动实体类，映射sv_activity表
 */
@Data
@TableName("sv_activity")
public class Activity {
    
    /** 活动ID */
    private Long id;
    
    /** 活动名称 */
    private String name;
    
    /** 活动状态 */
    private String status;
    
    /** 创建时间 */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
    
    /** 创建者ID */
    @TableField(value = "creator_id")
    private Long creatorId;
}
```

### 注意事项
- 使用 Lombok `@Data` 生成getter/setter
- 使用 `@TableName` 映射表名
- 使用 `@TableField` 映射列名（snake_case → camelCase）
- 避免在Entity中添加业务逻辑，仅保存属性

## 10. 服务层规范

```java
/**
 * 抽奖活动服务
 */
@Service
public class LotteryActivityService {
    
    @Autowired
    private LotteryActivityMapper activityMapper;
    
    /**
     * 创建抽奖活动
     *
     * @param request 活动创建请求
     * @param userId  当前用户ID
     * @return 创建的活动ID
     * @throws BusinessException 创建失败
     */
    @Transactional
    public Long createActivity(CreateActivityRequest request, Long userId) {
        Activity activity = new Activity();
        activity.setName(request.getName());
        activity.setCreatorId(userId);
        activity.setStatus("DRAFT");
        
        try {
            activityMapper.insert(activity);
            return activity.getId();
        } catch (Exception e) {
            log.error("创建活动失败，用户ID：[{}]，请求：[{}]", userId, request, e);
            throw new BusinessException("活动创建失败", e);
        }
    }
}
```

### 注意事项
- `@Transactional` 用于数据修改操作
- 事务范围应尽可能小，重型操作用消息队列处理
- Service中应有异常处理和日志记录
- 不应有业务无关的代码（日志除外）

## 11. Controller规范

```java
/**
 * 抽奖活动管理API
 */
@RestController
@RequestMapping("/api/lottery")
public class LotteryActivityController {
    
    @Autowired
    private LotteryActivityService lotteryActivityService;
    
    /**
     * 创建抽奖活动
     */
    @PostMapping("/activity")
    @Login  // 需要登录
    public R<CreateActivityResponse> createActivity(
            @Valid @RequestBody CreateActivityRequest request,
            @LoginUser LoginUser user) {
        try {
            Long activityId = lotteryActivityService.createActivity(
                request, user.getId());
            return R.ok(new CreateActivityResponse(activityId));
        } catch (BusinessException e) {
            return R.fail(e.getMessage());
        }
    }
}
```

### 注意事项
- 使用 `@Valid` 进行请求参数验证
- 使用统一响应格式 `R<T>`
- 使用 `@Login`、`@LoginUser` 处理认证
- Controller只负责HTTP转换，业务逻辑在Service

## 小结

这份规范涵盖：
1. ✅ 命名规范（包、类、方法、变量、常量）
2. ✅ 代码风格（注释、换行、缩进）
3. ✅ 异常处理（禁止吞异常、必须记日志）
4. ✅ 集合使用（深拷贝、遍历安全）
5. ✅ 并发安全（分布式锁、消息队列）
6. ✅ 日志规范（级别、格式、敏感信息）
7. ✅ SQL规范（关键字大写、分片键、分页）
8. ✅ 数据库设计（表名、索引、字段）
9. ✅ Entity/Model（Lombok、TableName）
10. ✅ 服务层（事务范围、异常处理）
11. ✅ Controller层（参数验证、响应格式）

**使用指南**：在生成Java代码或SQL语句时，参考本规范确保代码质量和一致性。