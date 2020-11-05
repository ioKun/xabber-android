package com.xabber.android.data.extension.groupchat.rights

import com.xabber.android.data.entity.ContactJid
import org.jivesoftware.smackx.xdata.packet.DataForm

class GroupRequestMemberRightsChangeIQ(val groupchatJid: ContactJid, val dataForm: DataForm)
    : GroupchatAbstractRightsIQ() {

    init {
        to = groupchatJid.jid
        type = Type.set
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder) = xml.apply {
        rightAngleBracket()
        append(dataForm.toXML())
    }

}