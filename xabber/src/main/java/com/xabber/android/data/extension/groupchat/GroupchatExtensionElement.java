package com.xabber.android.data.extension.groupchat;

import com.xabber.android.data.message.chat.groupchat.GroupchatManager;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

public class GroupchatExtensionElement implements ExtensionElement {

    public static final String NAMESPACE = GroupchatManager.NAMESPACE;
    public static final String ELEMENT = "x";

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    @Override
    public CharSequence toXML() {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        appendToXML(xml);
        xml.closeElement(this);
        return xml;
    }

    public void appendToXML(XmlStringBuilder xml) {
    }

}
