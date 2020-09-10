package com.xabber.android.data.extension.references.mutable.groupchat;

import com.xabber.android.data.extension.groupchat.GroupchatMemberExtensionElement;
import com.xabber.android.data.extension.references.mutable.Mutable;

import org.jivesoftware.smack.util.XmlStringBuilder;

public class GroupchatMemberReference extends Mutable {

    private GroupchatMemberExtensionElement user;

    public GroupchatMemberReference(int begin, int end, GroupchatMemberExtensionElement user) {
        super(begin, end);
        this.user = user;
    }

    public GroupchatMemberExtensionElement getUser() {
        return user;
    }

    public void setUser(GroupchatMemberExtensionElement user) {
        this.user = user;
    }

    @Override
    public void appendToXML(XmlStringBuilder xml) {
        if (user != null) xml.append(user.toXML());
    }
}