package com.xabber.android.data.groups

import com.xabber.android.R
import com.xabber.android.data.Application
import com.xabber.android.data.BaseIqResultUiListener
import com.xabber.android.data.OnLoadListener
import com.xabber.android.data.account.AccountManager
import com.xabber.android.data.connection.StanzaSender
import com.xabber.android.data.database.realmobjects.GroupInviteRealmObject
import com.xabber.android.data.database.repositories.GroupInviteRepository
import com.xabber.android.data.entity.AccountJid
import com.xabber.android.data.entity.ContactJid
import com.xabber.android.data.entity.NestedMap
import com.xabber.android.data.extension.blocking.BlockingManager
import com.xabber.android.data.extension.blocking.BlockingManager.BlockContactListener
import com.xabber.android.data.extension.groupchat.OnGroupSelectorListToolbarActionResult
import com.xabber.android.data.extension.groupchat.invite.incoming.DeclineGroupInviteIQ
import com.xabber.android.data.extension.groupchat.invite.incoming.IncomingInviteExtensionElement
import com.xabber.android.data.extension.groupchat.invite.outgoing.*
import com.xabber.android.data.extension.vcard.VCardManager
import com.xabber.android.data.log.LogManager
import com.xabber.android.data.message.MessageUpdateEvent
import com.xabber.android.data.message.NewMessageEvent
import com.xabber.android.data.message.chat.ChatManager
import com.xabber.android.data.message.chat.GroupChat
import com.xabber.android.data.roster.PresenceManager
import com.xabber.android.data.roster.RosterManager
import org.greenrobot.eventbus.EventBus
import org.jivesoftware.smack.ExceptionCallback
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

object GroupInviteManager: OnLoadListener {

    private val LOG_TAG = GroupInviteManager.javaClass.simpleName

    private val invitesMap = NestedMap<GroupInviteRealmObject>()

    override fun onLoad() {
        for (giro in GroupInviteRepository.getAllInvitationsForEnabledAccounts()) {
            invitesMap.put(giro.accountJid.toString(), giro.groupJid.toString(), giro)
        }
    }

    fun processIncomingInvite(inviteExtensionElement: IncomingInviteExtensionElement, account: AccountJid,
                              sender: ContactJid?, timestamp: Long) {
        try {
            val groupContactJid = ContactJid.from(inviteExtensionElement.groupJid)
            if (BlockingManager.getInstance().contactIsBlocked(account, groupContactJid)
                    || RosterManager.getInstance().accountIsSubscribedTo(account, groupContactJid)) return
            val inviteReason = inviteExtensionElement.getReason()
            val giro = GroupInviteRealmObject().apply {
                accountJid = account
                isIncoming = true
                groupJid = groupContactJid
                senderJid = sender
                reason = inviteReason
                date = timestamp
                isRead = false
            }
            VCardManager.getInstance().requestByUser(account, groupContactJid.jid)
            invitesMap.put(account.toString(), groupContactJid.toString(), giro)
            GroupInviteRepository.saveInviteToRealm(giro)
            ChatManager.getInstance().createGroupChat(account, groupContactJid).createFakeMessageForInvite(giro)
        } catch (e: Exception) {
            LogManager.exception(LOG_TAG, e)
        }
        EventBus.getDefault().post(NewMessageEvent())
        EventBus.getDefault().post(MessageUpdateEvent())
    }

    fun readInvite(accountJid: AccountJid, groupJid: ContactJid) {
        val giro = invitesMap[accountJid.toString(), groupJid.toString()]
        giro.isRead = true
        GroupInviteRepository.removeInviteFromRealm(accountJid, groupJid)
        GroupInviteRepository.saveInviteToRealm(giro)
        EventBus.getDefault().post(NewMessageEvent())
        EventBus.getDefault().post(MessageUpdateEvent())
    }

    fun acceptInvitation(accountJid: AccountJid, groupJid: ContactJid) {
        try {
            val name = (ChatManager.getInstance().getChat(accountJid, groupJid) as GroupChat?)!!.name
            PresenceManager.getInstance().acceptSubscription(accountJid, groupJid)
            RosterManager.getInstance().createContact(accountJid, groupJid, name, ArrayList())
            invitesMap.remove(accountJid.toString(), groupJid.toString())
            GroupInviteRepository.removeInviteFromRealm(accountJid, groupJid)
        } catch (e: java.lang.Exception) {
            LogManager.exception(LOG_TAG, e)
        }
        EventBus.getDefault().post(NewMessageEvent())
        EventBus.getDefault().post(MessageUpdateEvent())
    }

    fun declineInvitation(accountJid: AccountJid, groupJid: ContactJid) {
        Application.getInstance().runInBackgroundNetworkUserRequest {
            try {
                val groupChat = ChatManager.getInstance().getChat(accountJid, groupJid) as GroupChat?
                AccountManager.getInstance().getAccount(accountJid)!!.connection.sendIqWithResponseCallback(
                        DeclineGroupInviteIQ(groupChat!!),
                        { packet: Stanza? ->
                            if (packet is IQ && packet.type == IQ.Type.result) {
                                LogManager.i(LOG_TAG, "Invite from group " + groupJid.toString()
                                        + " to account " + accountJid.toString() + " successfully declined.")
                                invitesMap.remove(accountJid.toString(), groupJid.toString())
                                GroupInviteRepository.removeInviteFromRealm(accountJid, groupJid)
                                ChatManager.getInstance().removeChat(groupChat)
                                BlockingManager.getInstance().blockContact(accountJid, groupJid,
                                        object : BlockContactListener {
                                            override fun onSuccessBlock() {}
                                            override fun onErrorBlock() {}
                                        })
                            }
                        }
                ) { exception: java.lang.Exception ->
                    LogManager.e(LOG_TAG,
                            """Error to decline the invite from group $groupJid to account $accountJid!${exception.message}""")
                }
            } catch (e: java.lang.Exception) {
                LogManager.e(LOG_TAG,
                        """Error to decline the invite from group $groupJid to account $accountJid!${e.message}""")
            }
        }
        EventBus.getDefault().post(NewMessageEvent())
        EventBus.getDefault().post(MessageUpdateEvent())
    }

    fun hasInvite(accountJid: AccountJid, groupchatJid: ContactJid): Boolean {
        val giro = invitesMap[accountJid.toString(), groupchatJid.toString()]
        return giro != null && !BlockingManager.getInstance().contactIsBlocked(accountJid, groupchatJid)
    }

    fun getInvite(accountJid: AccountJid, groupJid: ContactJid): GroupInviteRealmObject? {
        return if (hasInvite(accountJid, groupJid)) invitesMap[accountJid.toString(), groupJid.toString()] else null
    }

    /**
     * Create and send IQ to group and Message with invitation to contact according to Direct Invitation.
     */
    fun sendGroupInvitations(account: AccountJid, groupJid: ContactJid, contactsToInvite: List<ContactJid>,
                             reason: String?, listener: BaseIqResultUiListener) {
        Application.getInstance().runInBackgroundNetworkUserRequest {
            val chat = ChatManager.getInstance().getChat(account, groupJid)
            if (chat is GroupChat) {
                val accountItem = AccountManager.getInstance().getAccount(account)
                if (accountItem != null) {
                    val connection: XMPPConnection = accountItem.connection
                    listener.onSend()
                    for (invite in contactsToInvite) {
                        val requestIQ = GroupInviteRequestIQ(chat as GroupChat?, invite)
                        requestIQ.setLetGroupSendInviteMessage(false)
                        if (reason != null && reason.isNotEmpty()) requestIQ.setReason(reason)
                        try {
                            connection.sendIqWithResponseCallback(requestIQ,
                                    { sendMessageWithInvite(account, groupJid, invite, reason, listener) }
                            ) { exception: java.lang.Exception? -> LogManager.exception(LOG_TAG, exception)}
                        } catch (e: java.lang.Exception) {
                            LogManager.exception(LOG_TAG, e)
                        }
                    }
                }
            }
            listener.onOtherError(null)
        }
    }

    /**
     * Sends a message with invite to group as direct invitation
     * Must be called only from @see #sendGroupInvitations
     */
    private fun sendMessageWithInvite(account: AccountJid, groupJid: ContactJid, contactToInviteJid: ContactJid,
                                      reason: String?, listener: BaseIqResultUiListener) {
        try {
            val inviteMessage = Message().apply {
                addBody(null, Application.getInstance().applicationContext
                        .getString(R.string.groupchat_legacy_invitation_body, groupJid.toString()))
                to = contactToInviteJid.jid
                type = Message.Type.chat
                addExtension(InviteMessageExtensionElement(groupJid, reason))
            }

            StanzaSender.sendStanza(account, inviteMessage) { packet1: Stanza ->
                if (packet1.error != null) {
                    listener.onOtherError(null)
                } else listener.onResult()
            }
        } catch (e: java.lang.Exception) {
            LogManager.exception(LOG_TAG, e)
            listener.onOtherError(e)
        }
    }

    fun requestGroupInvitationsList(account: AccountJid, groupchatJid: ContactJid, listener: StanzaListener,
                                    exceptionCallback: ExceptionCallback?) {
        Application.getInstance().runInBackgroundNetworkUserRequest {
            val chat = ChatManager.getInstance().getChat(account, groupchatJid)
            if (chat is GroupChat) {
                val accountItem = AccountManager.getInstance().getAccount(account)
                if (accountItem != null) {
                    val connection: XMPPConnection = accountItem.connection
                    val queryIQ = GroupchatInviteListQueryIQ(chat as GroupChat?)
                    try {
                        connection.sendIqWithResponseCallback(queryIQ, { packet: Stanza ->
                            if (packet is GroupchatInviteListResultIQ) {
                                if (groupchatJid.bareJid.equals(packet.getFrom().asBareJid())
                                        && account.bareJid.equals(packet.getTo().asBareJid())) {
                                    val listOfInvites = packet.listOfInvitedJids
                                    if (listOfInvites != null) {
                                        chat.listOfInvites = listOfInvites
                                    } else chat.listOfInvites = ArrayList()

                                }
                            }
                            listener.processStanza(packet)
                        }, exceptionCallback)
                    } catch (e: Exception) {
                        LogManager.exception(LOG_TAG, e)
                    }
                }
            }
        }
    }

    fun revokeGroupchatInvitation(account: AccountJid?, groupchatJid: ContactJid?, inviteJid: String) {
        Application.getInstance().runInBackgroundNetworkUserRequest {
            try {
                val groupChat = ChatManager.getInstance().getChat(account, groupchatJid) as GroupChat?
                val revokeIQ = GroupchatInviteListRevokeIQ(groupChat, inviteJid)
                val accountItem = AccountManager.getInstance().getAccount(account)
                accountItem?.connection?.sendIqWithResponseCallback(revokeIQ, { packet: Stanza? ->
                    if (packet is IQ) {
                        val success: Boolean
                        if (IQ.Type.result == packet.type) {
                            success = true
                            val chat = ChatManager.getInstance().getChat(account, groupchatJid)
                            if (chat is GroupChat) chat.listOfInvites.remove(inviteJid)

                        } else success = false

                        Application.getInstance().runOnUiThread {
                            for (listener in Application.getInstance().getUIListeners(OnGroupSelectorListToolbarActionResult::class.java)) {
                                if (success) {
                                    listener.onActionSuccess(account, groupchatJid, listOf(inviteJid))
                                } else listener.onActionFailure(account, groupchatJid, listOf(inviteJid))

                            }
                        }
                    }
                }) { Application.getInstance().runOnUiThread {
                        for (listener in Application.getInstance().getUIListeners(OnGroupSelectorListToolbarActionResult::class.java)) {
                            listener.onActionFailure(account, groupchatJid, listOf(inviteJid))
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                LogManager.exception(LOG_TAG, e)
            }
        }
    }

    fun revokeGroupchatInvitations(account: AccountJid?, groupchatJid: ContactJid?, inviteJids: Set<String>) {
        Application.getInstance().runInBackgroundNetworkUserRequest {
            val accountItem = AccountManager.getInstance().getAccount(account)
                    ?: return@runInBackgroundNetworkUserRequest
            val failedRevokeRequests = ArrayList<String>()
            val successfulRevokeRequests = ArrayList<String>()
            val unfinishedRequestCount = AtomicInteger(inviteJids.size)
            val chat = ChatManager.getInstance().getChat(account, groupchatJid)
            val groupChat: GroupChat?
            groupChat = if (chat is GroupChat) {
                chat
            } else null

            for (inviteJid in inviteJids) {
                try {
                    val revokeIQ = GroupchatInviteListRevokeIQ(groupChat, inviteJid)
                    accountItem.connection.sendIqWithResponseCallback(revokeIQ, { packet: Stanza? ->
                        if (packet is IQ) {
                            groupChat?.listOfInvites?.remove(inviteJid)
                            successfulRevokeRequests.add(inviteJid)
                            unfinishedRequestCount.getAndDecrement()
                            if (unfinishedRequestCount.get() == 0) {
                                Application.getInstance().runOnUiThread {
                                    for (listener in Application.getInstance()
                                            .getUIListeners(OnGroupSelectorListToolbarActionResult::class.java)) {
                                        when {
                                            failedRevokeRequests.size == 0 -> {
                                                listener.onActionSuccess(account, groupchatJid, successfulRevokeRequests)
                                            }
                                            successfulRevokeRequests.size > 0 -> {
                                                listener.onPartialSuccess(account, groupchatJid, successfulRevokeRequests,
                                                        failedRevokeRequests)
                                            }
                                            else -> {
                                                listener.onActionFailure(account, groupchatJid, failedRevokeRequests)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }) { failedRevokeRequests.add(inviteJid)
                        unfinishedRequestCount.getAndDecrement()
                        if (unfinishedRequestCount.get() == 0) {
                            Application.getInstance().runOnUiThread {
                                for (listener in Application.getInstance()
                                        .getUIListeners(OnGroupSelectorListToolbarActionResult::class.java)) {
                                    if (successfulRevokeRequests.size > 0) {
                                        listener.onPartialSuccess(account, groupchatJid, successfulRevokeRequests, failedRevokeRequests)
                                    } else listener.onActionFailure(account, groupchatJid, failedRevokeRequests)

                                }
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    LogManager.exception(LOG_TAG, e)
                    failedRevokeRequests.add(inviteJid)
                    unfinishedRequestCount.getAndDecrement()
                }
            }
        }
    }

}