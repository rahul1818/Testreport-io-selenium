# testreport-io (Selenium + TestNG)

Same report dashboard as Playwright [testreport.io-io](https://github.com/rahul1818/Testreport-io).

| Playwright (npm) | Selenium (Maven) |
|------------------|------------------|
| `npm i testreport.io-io` | Maven dependency below |
| `reporter: ['testreport.io-io/reporter']` | `@EnableTestReport` |
| `npx playwright test` | `mvn test` |
| `npx playwright-viewer serve` | **`mvn exec:java`** or **`.\serve.ps1`** |

## Quick start

### 1. Add dependency

```xml
<dependency>
  <groupId>io.github.rahul1818</groupId>
  <artifactId>testreport-io</artifactId>
  <version>1.0.4</version>
  <scope>test</scope>
</dependency>
```

### 2. Enable reporter

```java
@EnableTestReport
public class BaseTest { }
```

### 3. Run tests

```bash
mvn test
```

Report saved to: `custom-report/<runId>/results.json`

### 4. View report (same as playwright-viewer serve)

```powershell
.\serve.ps1
```

Or:

```powershell
mvn exec:java
```

Opens **http://localhost:4173** with your test data.

## Consumer project exec plugin (copy to your pom.xml)

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
  <configuration>
    <classpathScope>test</classpathScope>
    <mainClass>io.testreport.selenium.ReportViewerCli</mainClass>
    <arguments>
      <argument>serve</argument>
      <argument>--report-dir</argument>
      <argument>${project.basedir}/custom-report</argument>
    </arguments>
  </configuration>
</plugin>
```

Then run: `mvn exec:java`

## Optional settings

| Property | Default | Description |
|----------|---------|-------------|
| `testreport.outputDir` | `custom-report` | Report folder |
| `testreport.open` | `always` | Auto-open after `mvn test` |
| `testreport.port` | `4173` | Dashboard port |

## Publish

See [PUBLISHING.md](PUBLISHING.md).

## License

MIT
