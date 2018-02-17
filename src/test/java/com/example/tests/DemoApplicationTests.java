package com.example.tests;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.example.pageobjects.LoginPage;
import com.example.pageobjects.SessionMessagesPanel;
import com.example.pageobjects.TableXhtmlPage;
import com.example.pageobjects.ToolbarPanel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.Options;
import io.github.bonigarcia.SeleniumExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.web.client.LocalHostUriTemplateHandler;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Slf4j
@TestInstance(PER_CLASS)
@ExtendWith(SpringExtension.class)
@ExtendWith(SeleniumExtension.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

    @Options
    private ChromeOptions chromeOptions;
    @LocalServerPort
    private int port;
    private ObjectMapper objectMapper;
    private TestRestTemplate restAnonymousTemplate;
    private TestRestTemplate restUserAuthTemplate;
    private TestRestTemplate restAdminAuthTemplate;

    @Autowired
    DemoApplicationTests(
            Environment environment,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        JacksonTester.initFields(this, objectMapper);

        this.chromeOptions = initChromeOptions();
        initRestTemplate(restTemplateBuilder, environment);
    }

    private ChromeOptions initChromeOptions() {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--lang=pl", "--headless", "--window-size=1600,900");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("intl.accept_languages", "pl");
        chromeOptions.setExperimentalOption("prefs", prefs);

        return chromeOptions;
    }

    private void initRestTemplate(RestTemplateBuilder restTemplateBuilder, Environment environment) {
        this.restAnonymousTemplate = new TestRestTemplate(restTemplateBuilder
                .uriTemplateHandler(new LocalHostUriTemplateHandler(environment)).build());
        this.restUserAuthTemplate = new TestRestTemplate(restTemplateBuilder
                .uriTemplateHandler(new LocalHostUriTemplateHandler(environment)).build(), "user", "password");
        this.restAdminAuthTemplate = new TestRestTemplate(restTemplateBuilder
                .uriTemplateHandler(new LocalHostUriTemplateHandler(environment)).build(), "admin", "password");
    }

    @Test
    void contextLoads() {
        assertThat(port).isPositive();
    }

    @Test
    void home() {
        ResponseEntity<String> responseEntity = this.restAnonymousTemplate.getForEntity("/", String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.hasBody()).isTrue();
        assertThat(responseEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_UTF8);
        assertThat(responseEntity.getBody()).contains("principal");
    }

    @Test
    void homeLogging() {
        Logger appLoggers = (Logger) LoggerFactory.getLogger("com.example");
        appLoggers.setLevel(Level.OFF);

        ResponseEntity<String> responseEntity = this.restAnonymousTemplate.getForEntity("/", String.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        appLoggers.setLevel(Level.DEBUG);
        responseEntity = this.restAnonymousTemplate.getForEntity("/", String.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        appLoggers.setLevel(Level.INFO);
    }

    @Test
    void adminRedirectToLogin() {
        ResponseEntity<String> responseEntity = this.restAnonymousTemplate.getForEntity("/admin", String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(responseEntity.hasBody()).isFalse();
        assertThat(responseEntity.getHeaders().getLocation()).hasPath("/login");
    }

    @Test
    void managementUnauthorized() {
        ResponseEntity<String> responseEntity = this.restAnonymousTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.hasBody()).isTrue();
        assertThat(responseEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_UTF8);
        assertThat(responseEntity.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).contains("Basic realm=\"Realm\"");
        assertThat(responseEntity.getHeaders().getCacheControl()).isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    void managementForbidden() throws Exception {
        ResponseEntity<byte[]> responseEntity = this.restUserAuthTemplate.getForEntity("/actuator/metrics", byte[].class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(responseEntity.hasBody()).isTrue();
        assertThat(responseEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_UTF8);

        Map<String, Object> payload = objectMapper.readValue(responseEntity.getBody(), new TypeReference<Map<String, Object>>() {
        });
        assertThat(payload)
                .containsKey("message")
                .containsKey("status")
                .containsKey("timestamp")
                .containsKey("path");
    }

    @Test
    void managementAuthorized() throws Exception {
        ResponseEntity<byte[]> responseEntity = this.restAdminAuthTemplate.getForEntity("/actuator/info", byte[].class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.hasBody()).isTrue();
        assertThat(responseEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_UTF8);

        Map<String, Object> payload = objectMapper.readValue(responseEntity.getBody(), new TypeReference<Map<String, Object>>() {
        });
        assertThat(payload)
                .containsKey("git")
                .containsKey("build");
    }

    @Test
    void adminLoginFailPassword(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/admin");
        new LoginPage(webDriver).login("admin", "wrong");

        String content = webDriver.findElement(By.id("login-error")).getText();
        assertThat(content).contains("Invalid username and password.");
    }

    @Test
    void adminLoginFailUserName(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/admin");
        new LoginPage(webDriver).login("wrong", "password");

        String content = webDriver.findElement(By.id("login-error")).getText();
        assertThat(content).contains("Invalid username and password.");
    }

    @Test
    void hello(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/hello");
        new LoginPage(webDriver).login("user", "password");

        assertThat(webDriver.findElement(By.id("text")).getText()).contains("witaj świecie");

        webDriver.get("http://localhost:" + port + "/hello?lang=en");
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("hello word");

        webDriver.get("http://localhost:" + port + "/hello");
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("hello word");

        webDriver.get("http://localhost:" + port + "/hello?lang=pl");
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("witaj świecie");
    }

    @Test
    void adminLogin(ChromeDriver webDriver) throws Exception {
        webDriver.get("http://localhost:" + port + "/admin");
        new LoginPage(webDriver).login("admin", "password");

        String content = webDriver.findElement(By.tagName("pre")).getText();
        Map<String, Object> payload = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        assertThat(payload)
                .containsKey("principal")
                .containsKey("userList")
                .containsKey("user")
                .containsKey("message")
                .containsKey("buildProperties");
    }

    @Test
    void tableXhtml(ChromeDriver webDriver) throws Exception {
        webDriver.get("http://localhost:" + port + "/table.xhtml");
        new LoginPage(webDriver).login("user", "password");

        TableXhtmlPage tableXhtmlPage = new TableXhtmlPage(webDriver);
        tableXhtmlPage.waitForTableLoaded();
        assertThat(tableXhtmlPage.countRowsInTable()).isEqualTo(2);
        assertThat(tableXhtmlPage.findFirstRow().getText()).contains("hello word");

        // try to filter
        tableXhtmlPage.findFilterByLanguage().sendKeys("pol");
        tableXhtmlPage.waitForAjaxTableLoaded();
        assertThat(tableXhtmlPage.countRowsInTable()).isEqualTo(1);

        // try to sort
        tableXhtmlPage.findFilterByLanguage().sendKeys(Keys.chord(Keys.CONTROL, "c"), Keys.DELETE);
        tableXhtmlPage.findFilterByLanguage().clear();
        tableXhtmlPage.waitForAjaxTableLoaded();
        tableXhtmlPage.findSortByLanguage().click();
        tableXhtmlPage.waitForAjaxTableLoaded();
        assertThat(tableXhtmlPage.findFirstRow().getText()).contains("hello word");
        tableXhtmlPage.findSortByLanguage().click();
        tableXhtmlPage.waitForAjaxTableLoaded();
        assertThat(tableXhtmlPage.countRowsInTable()).isEqualTo(2);
        assertThat(tableXhtmlPage.findFirstRow().getText()).contains("witaj świecie");
    }

    @Test
    void changeLanguageInJsf(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/table.xhtml");
        new LoginPage(webDriver).login("user", "password");
        assertThat(new TableXhtmlPage(webDriver).findPageContent().getText()).contains("locale pl");

        new ToolbarPanel(webDriver).changeLanguage(Locale.ENGLISH);
        assertThat(new TableXhtmlPage(webDriver).findPageContent().getText()).contains("locale en");
    }

    @Test
    void changeLanguageInJsfAndMvc(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/hello");
        new LoginPage(webDriver).login("user", "password");
        TableXhtmlPage tableXhtmlPage = new TableXhtmlPage(webDriver);

        // locale is pl
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("witaj świecie");
        webDriver.get("http://localhost:" + port + "/table.xhtml");
        assertThat(tableXhtmlPage.findPageContent().getText()).contains("locale pl");

        // change locale to en in jsf
        new ToolbarPanel(webDriver).changeLanguage(Locale.ENGLISH);
        assertThat(tableXhtmlPage.findPageContent().getText()).contains("locale en");
        webDriver.get("http://localhost:" + port + "/hello");
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("hello word");

        // change locale to pl in mvc
        webDriver.get("http://localhost:" + port + "/hello?lang=pl");
        assertThat(webDriver.findElement(By.id("text")).getText()).contains("witaj świecie");
        webDriver.get("http://localhost:" + port + "/table.xhtml");
        assertThat(tableXhtmlPage.findPageContent().getText()).contains("locale pl");
    }

    @Test
    void jsfSessionMessages(ChromeDriver webDriver) {
        webDriver.get("http://localhost:" + port + "/index.xhtml");
        new LoginPage(webDriver).login("user", "password");

        SessionMessagesPanel sessionMessagesPanel = new SessionMessagesPanel(webDriver);
        sessionMessagesPanel.showInfo();
        sessionMessagesPanel.waitForAjax();
        assertThat(sessionMessagesPanel.findSessionMessage().getText()).contains("PrimeFaces Rocks.");

        sessionMessagesPanel.showWarn();
        sessionMessagesPanel.waitForAjax();
        assertThat(sessionMessagesPanel.findSessionMessage().getText()).contains("Watch out for PrimeFaces.");

        sessionMessagesPanel.showError();
        sessionMessagesPanel.waitForAjax();
        assertThat(sessionMessagesPanel.findSessionMessage().getText()).contains("Contact admin.");

        sessionMessagesPanel.showFatal();
        sessionMessagesPanel.waitForAjax();
        assertThat(sessionMessagesPanel.findSessionMessage().getText()).contains("System Error");
    }

}
