# 编码规范

- `必须 (must)`：表示强制性的要求，没有例外。
- `应当 (should)`：表示推荐的做法，但在某些情况下可以有例外。
- `可以 (may)`：表示允许的做法，但不是强制性的。
- `不应 (should not)`：表示不推荐的做法，但在某些情况下可以有例外。
- `不能 (must not)`：表示绝对禁止的做法，没有例外。

## 基础

- 你**必须**使用`Java 21`或更高版本进行开发。
- 你**必须**使用`UTF-8`编码保存所有源代码文件。
- 你**应当**使用Maven中央仓库或其他受信任的公共仓库来管理依赖。
- 你**不应**在import语句中使用通配符（*），即**不应**出现如`import java.util.*;`这样的语句。

## 代码风格

请遵循以下代码风格规范：

- 你**必须**使用4个空格进行缩进，而非制表符（Tab）。
- 你的代码每行长度**不应**超过120个字符，超过时请进行适当换行。
- 你**应当**使用大括号（{}）包围代码块，即使代码块只有一行。
- 你**必须**在方法/字段命名时使用驼峰命名法（camelCase），类命名时使用大驼峰命名法（PascalCase）。对于Json序列化字段，你**必须
  **使用下划线命名法（snake_case）。
- 你**必须**使用有意义的变量和方法命名，而**不能**使用单字母或无意义的名称。
- 你**应当**在方法和类上添加适当的JavaDoc注释，说明其功能和用法。
  - 你**必须**为public方法添加完整的JavaDoc注释，包括参数说明、返回值说明和异常说明。
- 你**应当**在适当的位置添加代码注释，解释复杂的逻辑或设计决策。

### 类成员格式

- 你**必须**按照以下顺序组织类成员：

    1. 常量（`public static final`）
    2. 静态字段（`static`）
    3. 实例字段
    4. 构造函数
    5. 公共方法（`public`）
    6. 受保护方法（`protected`）
    7. 私有方法（`private`）
    8. 静态方法（`static`）
- 你**应当**将被依赖的类成员放在前面，依赖它们的类成员放在后面。但当被依赖成员与上一条规则冲突时，**应当**遵循上一条规则。
- 你**应当**为类成员使用合适的访问修饰符，尽量使用最小的可见性。
- 当类成员过多时，你**应当**使用`/* REGION */`和`/* END REGION */`注释来划分不同的区域，以提高代码的可读性。

## git 提交规范

请遵循以下git提交规范：

- 你的提交信息**应当**简洁明了，描述所做的更改。
- 你的提交信息**应当**遵循以下格式：`<类型>: <描述>`
- 你**应当**使用以下类型之一：
    - `feat`：新功能
    - `fix`：修复bug
    - `docs`：文档更新
    - `style`：代码格式（不影响功能的更改）
    - `refactor`：代码重构（既不是新增功能，也不是修复bug的更改）
    - `test`：添加或修改测试代码
    - `chore`：构建过程或辅助工具的更改

## 关于异常处理

- SDK中提供了三种异常基类，分别用于不同的场景：

    - `IgnorableException`：可忽略异常，该类异常基于`RuntimeException`，无需强制捕获。当遇到不影响程序正常运行的异常时，可以抛出该异常以提示调用方，但不强制要求处理。
    - `UnignorableException`：不可忽略异常，该类异常基于`Exception`，强制要求捕获和处理。当遇到必须处理的异常时，应抛出该异常以确保调用方进行适当的处理。
    - `FatalError`：致命错误，该类异常基于`RuntimeException`，不能捕获。当遇到无法恢复的，应导致程序终止的严重错误时，应抛出该异常以提示程序终止运行。

- 在编写代码时，你**必须**基于上述三种异常类设计你的异常处理逻辑，**不能**直接使用Java标准库中的通用异常类（如`Exception`、
  `RuntimeException`、`Throwable`等）来表示业务异常。

- 你**可以**定义新的异常类，但这些异常类**必须**继承自上述三种异常类之一，以确保异常处理的一致性和可维护性。

- 你**不能**直接捕获`Exception`/`RuntimeException`/`Throwable`等通用异常类，而**必须**依照以下规则进行处理：

    - 对于`IgnorableException`，你**可以**选择捕获它以进行日志记录或其他非强制性处理，但也可以选择不捕获它，让程序继续运行。
    - 对于`UnignorableException`，你**必须**捕获它并进行日志记录及适当地处理，以确保程序的稳定性。
    - 对于`FatalError`，你**不能**捕获它，`Maibot-Core`的全局异常处理会捕获该异常并进行日志记录及程序终止处理。
    - 对于其他异常，你**必须**明确指出你要捕获的类型。如要再次抛出，你**必须**将其用以上三种异常基类中的某种包装后再重新抛出。

## 关于日志记录

Maibot-JE使用SLF4J作为日志接口，Logback作为日志实现。请遵循以下日志记录规范：

- 你**应当**在每个单例类中定义一个私有的静态日志记录器实例，例如：

    ```java
    public class YourClassName {
        private static final Logger log = LoggerFactory.getLogger(YourClassName.class);
    }
    ```

- 对于非单例类，你**可以**选择定义一个实例级别的日志记录器，但应对不同实例的日志记录器命名进行区别，以便进行区分、调试，例如：

    ```java
    public class YourClassName {
        private final Logger log;
    
        public YourClassName() {
            this.log = LoggerFactory.getLogger(this.getClass() + "@" + Integer.toHexString(System.identityHashCode(this)));
        }
    }
    ```

- 在记录日志时，请遵循以下规则：

    - 你**应当**使用占位符语法，而**不应**使用非字符串拼接来记录变量，例如：
      ```java
          void logExample(String userName, int userId) {
              // 推荐用法
              log.info("User {} with ID {} has logged in.", userName, userId);
              // 不推荐用法 
              log.info("User " + userName + " with ID " + userId + " has logged in.");
          }
      ```
    - 在捕获异常时，你**应当**使用日志记录器的重载方法记录异常堆栈信息，而**不应**手动拼接异常堆栈信息字符串，例如：
      ```java
          void logExceptionExample() {
              try {
                  // 可能抛出异常的代码
              } catch (UnignorableException e) {
                  // 推荐用法
                  log.error("An error occurred while processing the request.", e);
                  // 不推荐用法
                  log.error("An error occurred while processing the request: " + e.getMessage());
              }
          }
      ```
    - 你**应当**根据日志的重要性选择合适的日志级别（TRACE, DEBUG, INFO, WARN, ERROR），而**不应**一直使用同一个日志级别，例如：
      ```java
          void logLevelExample(boolean isDebugMode) {
              try {
                  log.info("Operation started.");
                  // Do something that may throw an exception
                  log.debug("Some debug information.");
              } catch (UnignorableException e) {
                  log.warn("An error occurred, but it's recoverable.", e);
              }
          }
      ```