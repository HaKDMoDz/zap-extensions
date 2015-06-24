/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.pscanrulesBeta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.*;

import net.htmlparser.jericho.Source;

import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HtmlParameter;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.extension.pscan.PluginPassiveScanner;
import org.zaproxy.zap.extension.pscan.PassiveScanThread;

public class InformationDisclosureInURL extends PluginPassiveScanner {

	private PassiveScanThread parent = null;
	private static final String URLSensitiveInformationFile = "xml/URL-information-disclosure-messages.txt";
	private static final Logger logger = Logger.getLogger(InformationDisclosureInURL.class);
	private List<String> messages = null;

	
	@Override
	public void scanHttpRequestSend(HttpMessage msg, int id) {
		TreeSet<HtmlParameter> urlParams = msg.getUrlParams();
		for (HtmlParameter urlParam : urlParams) {
			if (doesParamNameContainsSensitiveInformation(urlParam.getName())) {
				this.raiseAlert(msg, id, urlParam.getName(), urlParam.getValue(), 
						"The URL contains potentially sensitive information");
			}
			if (isCreditCard(urlParam.getValue())) {
				this.raiseAlert(msg, id, urlParam.getName(), urlParam.getValue(),
						"The URL appears to contain credit card information");
			}
			if (isEmailAddress(urlParam.getValue())) {
				this.raiseAlert(msg, id, urlParam.getName(), urlParam.getValue(),
						"The URL contains email address(es)");
			}
			if (isUsSSN(urlParam.getValue())) {
				this.raiseAlert(msg, id, urlParam.getName(), urlParam.getValue(),
						"The URL appears to contain US Social Security Number(s)");
			}
		}
	}

	@Override
	public void scanHttpResponseReceive(HttpMessage msg, int id, Source source) {
		
	}
	
	private void raiseAlert(HttpMessage msg, int id, String param, String evidence, String other) {
		Alert alert = new Alert(getPluginId(), Alert.RISK_INFO, Alert.CONFIDENCE_MEDIUM, 
		    	getName());
		    	alert.setDetail(
		    			"The request appeared to contain sensitive information leaked in the URL. This can violate PCI and most organizational compliance policies. You can configure the list of strings for this check to add or remove values specific to your environment", 
		    	    msg.getRequestHeader().getURI().toString(),
		    	    param,
		    	    other, 
		    	    "",
		    	    "Do not pass sensitive information in URI's", 
		            "", 
					evidence,	// Evidence
					0,	// TODO CWE Id
		            13,	// WASC Id - Info leakage
		            msg);
	
    	parent.raiseAlert(id, alert);
	}
	
	private List<String> loadFile(String file) {
		List<String> strings = new ArrayList<String>();
		BufferedReader reader = null;
		File f = new File(Constant.getZapHome() + File.separator + file);
		if (! f.exists()) {
			logger.error("No such file: " + f.getAbsolutePath());
			return strings;
		}
		try {
			String line;
			reader = new BufferedReader(new FileReader(f));
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("#")) {
					strings.add(line.trim().toLowerCase());
				}
			}
		} catch (IOException e) {
			logger.debug("Error on opening/reading debug error file. Error: " + e.getMessage(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();			
				}
				catch (IOException e) {
					logger.debug("Error on closing the file reader. Error: " + e.getMessage(), e);
				}
			}
		}
		return strings;
	}
	
	private boolean doesParamNameContainsSensitiveInformation (String paramName) {
		if (this.messages == null) {
			this.messages = loadFile(URLSensitiveInformationFile);
		}
		String ciParamName = paramName.toLowerCase();
		for (String msg : this.messages) {
			if (ciParamName.contains(msg)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setParent(PassiveScanThread parent) {
		this.parent = parent;
	}

	@Override
	public String getName() {
		return "Information Disclosure - Sensitive Informations in URL";
	}
	
	@Override
	public int getPluginId() {
		return 10024;
	}
	
	private boolean isEmailAddress(String emailAddress) {
		Pattern emailAddressPattern = Pattern.compile("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}\\b");
		Matcher matcher = emailAddressPattern.matcher(emailAddress);
		if (matcher.find()) {
			return true;
		}
		return false;
	}
	
	private boolean isCreditCard(String creditCard) {
		Pattern creditCardPattern = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|(?:2131|1800|35\\d{3})\\d{11})\\b");
		Matcher matcher = creditCardPattern.matcher(creditCard);
		if (matcher.find()) {
			return true;
		}
		return false;
	}
	
	private boolean isUsSSN(String usSSN) {
		Pattern usSSNPattern = Pattern.compile("\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b");
		Matcher matcher = usSSNPattern.matcher(usSSN);
		if (matcher.find()){
			return true;
		}
		return false;
	}
	
	private static void testUrl (String url) {
		InformationDisclosureInURL idiu = new InformationDisclosureInURL();
		System.out.println("Test URL: " + url);
		if (idiu.doesParamNameContainsSensitiveInformation(url)) {
			System.out.println(" contains sensitive info: " + idiu.doesParamNameContainsSensitiveInformation(url));
		}
		if (idiu.isCreditCard(url)) {
			System.out.println(" contains credit card: " + idiu.isCreditCard(url));
		}
		if (idiu.isEmailAddress(url)) {
			System.out.println(" contains email addr: " + idiu.isEmailAddress(url));
		}
		if (idiu.isUsSSN(url)) {
			System.out.println(" contains SSN: " + idiu.isUsSSN(url));
		}
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println("These should pass:");
		testUrl("http://www.google.com");
		testUrl("http://www.google.com/this/should/be/safe");

		System.out.println("These should fail:");
		testUrl("http://www.google.com/aaa?email=test@test.com");
		testUrl("http://www.google.com/bbb?aaa=bbb&email=test@test.com");
		testUrl("http://www.google.com/ccc?user=tester");
		testUrl("http://www.google.com/ddd?ssn=987-65-4320");
		testUrl("http://www.google.com/eee?ccn=378282246310005");
		
		
	}


}
