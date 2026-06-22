package io.testreport.selenium.sample;

import io.testreport.selenium.EnableTestReport;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@EnableTestReport
public class SampleSearchTest {

  private WebDriver driver;

  @BeforeMethod
  public void setUp(ITestResult result) {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new");
    driver = new ChromeDriver(options);
    result.setAttribute("driver", driver);
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test(description = "Open example.com and verify title")
  public void shouldOpenExampleDotCom() {
    driver.get("https://example.com");
    Assert.assertTrue(driver.getTitle().contains("Example"));
  }

  @Test(description = "Demonstrate flaky retry behavior", enabled = false)
  public void flakyExample() {
    driver.get("https://example.com");
    Assert.assertEquals(driver.getTitle(), "Wrong title on purpose");
  }
}
