# Selenium Reporter (Maven Library)

Maven library for **Selenium + TestNG** — same report dashboard as the Playwright npm package.

| Playwright (npm) | Selenium (Maven) |
|------------------|------------------|
| `testreport.io-io/reporter` | `io.testreport:reporter` |
| `npx playwright-viewer serve` | `mvn exec:java -Dexec.mainClass=io.testreport.selenium.ReportViewerCli` |

## Install in your Selenium Maven project

```xml
<dependency>
  <groupId>io.testreport</groupId>
  <artifactId>reporter</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
```

Build locally:

```bash
cd selenium-viewer-main
mvn clean install
```

**Publish to Maven Central** (public registry — like `npm publish`): see **[PUBLISHING.md](PUBLISHING.md)**.

## 1. Add TestNG listener

**`testng.xml`**

```xml
<listeners>
  <listener class-name="io.testreport.selenium.SeleniumTestReporter"/>
</listeners>
```

**Or on a base class**

```java
@Listeners(SeleniumTestReporter.class)
public class BaseTest { }
```

## 2. Run tests

```bash
mvn test
```

Creates `custom-report/<runId>/results.json`.

### Optional properties

| Property | Default |
|----------|---------|
| `testreport.outputDir` | `custom-report` |
| `testreport.fileName` | `results.json` |
| `testreport.projectName` | *(empty)* |

### Screenshots

```java
@BeforeMethod
public void setUp(ITestResult result) {
  driver = new ChromeDriver();
  result.setAttribute("driver", driver);
}
```

## 3. Open dashboard (same UI as Playwright)

```bash
mvn exec:java -Dexec.mainClass=io.testreport.selenium.ReportViewerCli -Dexec.args="serve"
```

Custom port / folder:

```bash
mvn exec:java -Dexec.mainClass=io.testreport.selenium.ReportViewerCli -Dexec.args="serve --port 4300 --report-dir custom-report"
```

Or after `mvn package`:

```bash
java -cp target/reporter-1.0.0.jar;target/dependency/* io.testreport.selenium.ReportViewerCli serve
```

## Library contents

```
io.testreport.selenium.SeleniumTestReporter   TestNG listener → writes JSON
io.testreport.selenium.ReportViewerServer     Embedded HTTP dashboard server
io.testreport.selenium.ReportViewerCli        CLI to start the viewer
```

Dashboard HTML and assets are bundled inside the JAR (`src/main/resources/testreport/`).

## License

MIT
