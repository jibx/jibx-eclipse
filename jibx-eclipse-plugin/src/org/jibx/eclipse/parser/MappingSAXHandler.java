package org.jibx.eclipse.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author Michael McMahon  <michael.mcmahon@activewire.net>
 *
 */
public class MappingSAXHandler extends DefaultHandler {
	
	private List mappedClasses = new ArrayList();
	
	public List getMappedClasses () {
		return mappedClasses;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		  SAXParserFactory factory = SAXParserFactory.newInstance();
		  try {
		        SAXParser saxParser = factory.newSAXParser();
		        saxParser.parse( new File("../connectorsWeb/src/main/jibx/retitleCandidatesVO-binding.xml"), new MappingSAXHandler() );

		  } catch (Throwable err) {
		        err.printStackTrace ();
		  }
	}

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		if ("mapping".equals(qName)) {
			String s = attributes.getValue("class");
			if (null != s)
				mappedClasses.add(s);
		} else if ("structure".equals(qName)) {
			String s = attributes.getValue("type");
			if (null != s)
				mappedClasses.add(s);				
		}
	}

}
