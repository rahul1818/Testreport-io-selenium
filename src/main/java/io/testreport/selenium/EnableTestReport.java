package io.testreport.selenium;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.testng.annotations.Listeners;

/**
 * One-line reporter setup (same idea as {@code reporter: ['testreport.io-io/reporter']} in Playwright).
 *
 * <pre>{@code
 * @EnableTestReport
 * public class BaseTest { }
 * }</pre>
 *
 * Optional when the dependency is on the test classpath — TestNG also loads
 * {@link SeleniumTestReporter} automatically via {@code META-INF/services}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Listeners(SeleniumTestReporter.class)
public @interface EnableTestReport {}
