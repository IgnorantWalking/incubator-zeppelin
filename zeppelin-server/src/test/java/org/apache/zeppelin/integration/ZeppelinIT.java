/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.integration;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.AbstractZeppelinIT;
import org.apache.zeppelin.WebDriverManager;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.junit.rules.ErrorCollector;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test Zeppelin with web browser.
 *
 * To test, ZeppelinServer should be running on port 8080
 * On OSX, you'll need firefox 42.0 installed, then you can run with
 *
 * PATH=~/Applications/Firefox.app/Contents/MacOS/:$PATH TEST_SELENIUM="" \
 *    mvn -Dtest=org.apache.zeppelin.integration.ZeppelinIT -Denforcer.skip=true \
 *    test -pl zeppelin-server
 *
 */
public class ZeppelinIT extends AbstractZeppelinIT {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinIT.class);

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @Before
  public void startUp() {
    if (!endToEndTestEnabled()) {
      return;
    }
    driver = WebDriverManager.getWebDriver();
  }

  @After
  public void tearDown() {
    if (!endToEndTestEnabled()) {
      return;
    }

    driver.quit();
  }

  @Test
  public void testAngularDisplay() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      /*
       * print angular template
       * %angular <div id='angularTestButton' ng-click='myVar=myVar+1'>BindingTest_{{myVar}}_</div>
       */
      setTextOfParagraph(1, "println(\"%angular <div id=\\'angularTestButton\\' ng-click=\\'myVar=myVar+1\\'>BindingTest_{{myVar}}_</div>\")");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      waitForText("BindingTest__", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind variable
       * z.angularBind("myVar", 1)
       */
      assertEquals(1, driver.findElements(By.xpath(getParagraphXPath(2) + "//textarea")).size());
      setTextOfParagraph(2, "z.angularBind(\"myVar\", 1)");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));


      /*
       * print variable
       * print("myVar="+z.angular("myVar"))
       */
      setTextOfParagraph(3, "print(\"myVar=\"+z.angular(\"myVar\"))");
      runParagraph(3);
      waitForParagraph(3, "FINISHED");

      // check expected text
      waitForText("myVar=1", By.xpath(
              getParagraphXPath(3) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      /*
       * Click element
       */
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_2_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Register watcher
       * z.angularWatch("myVar", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext) => {
       *   z.run(2, context)
       * }
       */
      setTextOfParagraph(4, "z.angularWatch(\"myVar\", (before:Object, after:Object, context:org.apache.zeppelin.interpreter.InterpreterContext)=>{ z.run(2, context)})");
      runParagraph(4);
      waitForParagraph(4, "FINISHED");


      /*
       * Click element, again and see watcher works
       */
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]")).click();

      // check expected text
      waitForText("BindingTest_3_", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));
      waitForParagraph(3, "FINISHED");

      // check expected text by watcher
      waitForText("myVar=3", By.xpath(
              getParagraphXPath(3) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      /*
       * Unbind
       * z.angularUnbind("myVar")
       */
      setTextOfParagraph(5, "z.angularUnbind(\"myVar\")");
      runParagraph(5);
      waitForParagraph(5, "FINISHED");

      // check expected text
      waitForText("BindingTest__",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      /*
       * Bind again and see rebind works.
       */
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // check expected text
      waitForText("BindingTest_1_",
          By.xpath(getParagraphXPath(1) + "//div[@id=\"angularTestButton\"]"));

      driver.findElement(By.xpath("//*[@id='main']/div//h3/span/button[@tooltip='Remove the notebook']"))
          .sendKeys(Keys.ENTER);
      sleep(1000, true);
      driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'delete this notebook')]" +
          "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
      sleep(100, true);

      LOG.info("testCreateNotebook Test executed");
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularDisplay ", e);
    }
  }

  @Test
  public void testSparkInterpreterDependencyLoading() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }
    try {
      // navigate to interpreter page
      WebElement interpreterLink = driver.findElement(By.linkText("Interpreter"));
      interpreterLink.click();

      // add new dependency to spark interpreter
      WebElement sparkEditBtn = pollingWait(By.xpath("//div[h3[text()[contains(.,'spark')]]]//button[contains(.,'edit')]"),
          MAX_BROWSER_TIMEOUT_SEC);
      sparkEditBtn.click();
      WebElement depArtifact = driver.findElement(By.xpath("//input[@ng-model='setting.depArtifact']"));
      String artifact = "org.apache.commons:commons-csv:1.1";
      depArtifact.sendKeys(artifact);
      driver.findElement(By.xpath("//button[contains(.,'Save')]")).submit();
      driver.switchTo().alert().accept();

      driver.navigate().back();
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      setTextOfParagraph(1, "import org.apache.commons.csv.CSVFormat");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // check expected text
      WebElement paragraph1Result = driver.findElement(By.xpath(
          getParagraphXPath(1) + "//div[@class=\"tableDisplay\"]"));

      collector.checkThat("Paragraph from ZeppelinIT of testSparkInterpreterDependencyLoading result: ",
          paragraph1Result.getText().toString(), CoreMatchers.containsString(
              "import org.apache.commons.csv.CSVFormat"
          )
      );

      //delete created notebook for cleanup.
      deleteTestNotebook(driver);
      sleep(1000, true);

      // reset dependency
      interpreterLink.click();
      sparkEditBtn = pollingWait(By.xpath("//div[h3[text()[contains(.,'spark')]]]//button[contains(.,'edit')]"),
          MAX_BROWSER_TIMEOUT_SEC);
      sparkEditBtn.click();
      WebElement testDepRemoveBtn = driver.findElement(By.xpath("//tr[descendant::text()[contains(.,'" +
          artifact + "')]]/td[3]/div"));
      sleep(5000, true);
      testDepRemoveBtn.click();
      driver.findElement(By.xpath("//button[contains(.,'Save')]")).submit();
      driver.switchTo().alert().accept();
    } catch (Exception e) {
      handleException("Exception in ZeppelinIT while testSparkInterpreterDependencyLoading ", e);
    }
  }

  @Test
  public void testAngularRunParagraph() throws Exception {
    if (!endToEndTestEnabled()) {
      return;
    }

    try {
      createNewNote();

      // wait for first paragraph's " READY " status text
      waitForParagraph(1, "READY");

      // Create 1st paragraph
      setTextOfParagraph(1,
              "%angular <div id=\\'angularRunParagraph\\'>Run second paragraph</div>");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");
      waitForText("Run second paragraph", By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularRunParagraph\"]"));

      // Create 2nd paragraph
      setTextOfParagraph(2, "%sh echo TEST");
      runParagraph(2);
      waitForParagraph(2, "FINISHED");

      // Get 2nd paragraph id
      final String secondParagraphId = driver.findElement(By.xpath(getParagraphXPath(2)
              + "//div[@class=\"control ng-scope\"]//ul[@class=\"dropdown-menu\"]/li[1]"))
              .getAttribute("textContent");

      assertTrue("Cannot find paragraph id for the 2nd paragraph", isNotBlank(secondParagraphId));

      // Update first paragraph to call z.runParagraph() with 2nd paragraph id
      setTextOfParagraph(1,
              "%angular <div id=\\'angularRunParagraph\\' ng-click=\\'z.runParagraph(\""
                      + secondParagraphId.trim()
                      + "\")\\'>Run second paragraph</div>");
      runParagraph(1);
      waitForParagraph(1, "FINISHED");

      // Set new text value for 2nd paragraph
      setTextOfParagraph(2, "%sh echo NEW_VALUE");

      // Click on 1 paragraph to trigger z.runParagraph() function
      driver.findElement(By.xpath(
              getParagraphXPath(1) + "//div[@id=\"angularRunParagraph\"]")).click();

      waitForParagraph(2, "FINISHED");

      // Check that 2nd paragraph has been executed
      waitForText("NEW_VALUE", By.xpath(
              getParagraphXPath(2) + "//div[contains(@id,\"_text\") and @class=\"text\"]"));

      //delete created notebook for cleanup.
      deleteTestNotebook(driver);
      sleep(1000, true);

      LOG.info("testAngularRunParagraph Test executed");
    }  catch (Exception e) {
      handleException("Exception in ZeppelinIT while testAngularRunParagraph", e);
    }

  }
}
