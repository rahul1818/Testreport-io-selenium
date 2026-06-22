# Selenium Reporter (Maven Library)

Maven library for **Selenium + TestNG** — same report dashboard as the Playwright npm package.

| Playwright (npm) | Selenium (Maven) |
|------------------|------------------|
| `testreport.io-io/reporter` | `io.testreport:reporter` |
| `reporter: ['testreport.io-io/reporter']` | `@EnableTestReport` on your base test class |
| Report opens after `npx playwright test` | Report opens after `mvn test` |

## Quick start (2 steps — same as Playwright)

### 1. Add dependency

```xml
<dependency>
  <groupId>io.github.rahul1818</groupId>
  <artifactId>reporter</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
```

### 2. One line — enable reporter

**Java (recommended — same as Playwright config line)**

```java
import io.testreport.selenium.EnableTestReport;

@EnableTestReport
public class BaseTest { }
```

**Or `testng.xml`**

```xml
<listeners>
  <listener class-name="io.testreport.selenium.SeleniumTestReporter"/>
</listeners>
```

> If the dependency is on the test classpath, TestNG also auto-loads the reporter via `META-INF/services` — the one line above is optional but recommended.

### 3. Run tests

```bash
mvn test
```

That’s it. The JSON report is written and the **dashboard opens automatically** in your browser (skipped on CI by default).

## Screenshots (optional)

```java
@BeforeMethod
public void setUp(ITestResult result) {
  driver = new ChromeDriver();
  result.setAttribute("driver", driver);
}
```

## Optional settings

| Property | Default | Description |
|----------|---------|-------------|
| `testreport.outputDir` | `custom-report` | Report folder |
| `testreport.open` | `always` | `always`, `never`, or `on-failure` |
| `testreport.port` | `4173` | Dashboard port |
| `testreport.projectName` | *(empty)* | Project name in report |

Example in `pom.xml`:

```xml
<properties>
  <testreport.open>always</testreport.open>
</properties>
```

## Manual viewer (optional)

```bash
mvn exec:java -Dexec.mainClass=io.testreport.selenium.ReportViewerCli -Dexec.args="serve"
```

## Publish to Maven Central

See **[PUBLISHING.md](PUBLISHING.md)**.

## License

MIT
