package com.xabber.android.data.extension.reliablemessagedelivery;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

public class ReceiptRequestElement implements ExtensionElement {

    public static final String ELEMENT = "request";
    private static final String NAMESPACE = "http://xabber.com/protocol/delivery";

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public XmlStringBuilder toXML() {
        XmlStringBuilder xmlStringBuilder = new XmlStringBuilder(this);
        xmlStringBuilder.closeEmptyElement();
        return xmlStringBuilder;
    }
}
