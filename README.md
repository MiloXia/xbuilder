# Phantom Type Builder Pattern
Inspired by [type safe builder pattern in java](https://michid.wordpress.com/2008/08/13/type-safe-builder-pattern-in-java/), use JSR 269 API to reduce boilerplate code.

# Build

`mvn clean install`

# Use @Builder

maven add:

```xml
<dependency>
    <groupId>com.mx</groupId>
    <artifactId>xbuilder</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```
In your java code:

```java
@Builder
public class User {
    private final String name;
    private final String password;

    private User(String name, String password) {
        this.name = name;
        this.password = password;
    }
}
...
User user = User.build(User.builder().name("admin").password("123456"));
```

