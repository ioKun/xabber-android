package com.xabber.android.ui.fragment.groups

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.widget.NestedScrollView
import com.xabber.android.R
import com.xabber.android.data.Application
import com.xabber.android.data.SettingsManager
import com.xabber.android.data.account.AccountManager
import com.xabber.android.data.entity.AccountJid
import com.xabber.android.data.entity.ContactJid
import com.xabber.android.data.extension.avatar.AvatarManager
import com.xabber.android.data.extension.groupchat.create.CreateGroupchatIqResultListener
import com.xabber.android.data.message.chat.groupchat.GroupchatIndexType
import com.xabber.android.data.message.chat.groupchat.GroupchatManager
import com.xabber.android.data.message.chat.groupchat.GroupchatMembershipType
import com.xabber.android.data.message.chat.groupchat.GroupchatPrivacyType
import com.xabber.android.data.roster.RosterManager
import com.xabber.android.ui.activity.ChatActivity
import com.xabber.android.ui.activity.CreateGroupActivity
import com.xabber.android.ui.color.ColorManager
import com.xabber.android.ui.fragment.CircleEditorFragment
import com.xabber.android.ui.widget.AccountSpinner
import com.xabber.android.utils.StringUtils
import kotlinx.android.synthetic.main.activity_fingerprint.*

@SuppressLint("SetTextI18n")
class CreateGroupFragment: CircleEditorFragment(), CreateGroupchatIqResultListener, AccountSpinner.Listener {

    private var listenerActivity: Listener? = null

    private var isIncognito: Boolean = false

    private var isAccountSelected = AccountManager.getInstance().enabledAccounts.size == 1

    private lateinit var accountSpinner: AccountSpinner
    private lateinit var exceptSpinnerLayout: NestedScrollView

    private lateinit var groupNameEt: EditText
    private lateinit var groupNameTv: TextView
    private lateinit var groupNameVw: View

    private lateinit var groupJidEt: EditText
    private lateinit var groupJidTv: TextView
    private lateinit var groupjidVw: View
    private lateinit var errorTv: TextView

    private lateinit var serverTv: TextView
    private lateinit var serverIv: ImageView

    private lateinit var groupDescriptionEt: EditText
    private lateinit var groupDescriptionTv: TextView
    private lateinit var groupDescriptionVw: View

    private lateinit var membershipTv: TextView
    private lateinit var membershipRg: RadioGroup

    private lateinit var indexTv: TextView
    private lateinit var indexRg: RadioGroup

    private lateinit var circlesLayout: LinearLayout

    private var isEnteredManually = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Listener){
            listenerActivity = context
        } else {
            throw IllegalArgumentException("Parent activity must implement CreateGroupFragment.Listener!")
        }
    }

    override fun onResume() {
        if (AccountManager.getInstance().enabledAccounts.size == 1){
            setAccount(AccountManager.getInstance().firstAccount)
            circlesLayout.visibility = View.VISIBLE
            setAccountCircles()
            updateCircles()
        }
        super.onResume()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.create_groupchat_fragment, container, false)

        isIncognito = arguments?.getBoolean(ARGUMENT_IS_INCOGNITO) ?: false

        initializeViewsVars(view)

        colorizeEverything(AccountManager.getInstance().firstAccount)

        if (!isInitialized && AccountManager.getInstance().enabledAccounts.isNotEmpty()) initRecyclerView(view)
        setupAccountSpinner()
        setupGroupNameEt()
        setupGroupJidEt()
        setupMembership()
        setupIndex()
        setupServerTv()

        return view
    }

//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putParcelable(SAVED_ACCOUNT, getAccount() ?: accountSpinner.selected)
//        if (!errorTv.text.isNullOrEmpty()) outState.putString(SAVED_ERROR, errorTv.text.toString())
//        outState.putString(SAVED_GROUP_DESCRIPTION, groupDescriptionEt.text.toString())
//        outState.putString(SAVED_GROUP_JID, groupJidEt.text.toString())
//        outState.putString(SAVED_GROUP_NAME, groupNameEt.text.toString())
//        outState.putString(SAVED_GROUP_SERVER, serverTv.text.toString())
//    }
//
//    override fun onViewStateRestored(savedInstanceState: Bundle?) {
//        super.onViewStateRestored(savedInstanceState)
//    }

    private fun initializeViewsVars(view: View){
        accountSpinner = view.findViewById(R.id.contact_account)
        exceptSpinnerLayout = view.findViewById(R.id.except_spinner)

        groupNameEt = view.findViewById(R.id.create_groupchat_name_et)
        groupNameTv = view.findViewById(R.id.groupchat_name_hint)
        groupNameVw = view.findViewById(R.id.groupchat_name_vw)

        groupJidEt = view.findViewById(R.id.groupchat_jid_et)
        groupJidTv = view.findViewById(R.id.groupchat_jid_hint)
        groupjidVw = view.findViewById(R.id.groupchat_jid_vw)
        errorTv = view.findViewById(R.id.error_view)

        serverIv = view.findViewById(R.id.server_iv)
        serverTv = view.findViewById(R.id.server_tv)

        membershipTv = view.findViewById(R.id.groupchat_membership_hint)
        membershipRg = view.findViewById(R.id.groupchat_membership_rg)

        indexTv = view.findViewById(R.id.groupchat_indexed_hint)
        indexRg = view.findViewById(R.id.groupchat_indexed_rg)

        groupDescriptionEt = view.findViewById(R.id.groupchat_description_et)
        groupDescriptionTv = view.findViewById(R.id.groupchat_description_hint)
        groupDescriptionVw = view.findViewById(R.id.groupchat_description_vw)

        circlesLayout = view.findViewById(R.id.circles_layout)
    }

    private fun setupAccountSpinner() {
        if (AccountManager.getInstance().enabledAccounts.size <= 1) {
            accountSpinner.visibility = View.GONE
            (exceptSpinnerLayout.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 0
            exceptSpinnerLayout.requestLayout()

        } else {
            val jids = AccountManager.getInstance().enabledAccounts.toList().sortedWith { o1, o2 -> o1.compareTo(o2) }

            val avatars = mutableListOf<Drawable>()
            for (jid in jids){
                avatars.add(AvatarManager.getInstance().getAccountAvatar(jid))
            }

            val nicknames = mutableListOf<String?>()
            for (jid in jids){
                val name = RosterManager.getInstance().getBestContact(jid, ContactJid.from(jid.fullJid.asBareJid())).name
                if (!name.isNullOrEmpty()){
                    nicknames.add(name)
                } else {
                    nicknames.add(null)
                }
            }

            accountSpinner.setup(getString(R.string.create_to), getString(R.string.choose_account), jids, avatars,
                    nicknames, this)
        }
    }

    private fun setupGroupNameEt(){
        groupNameEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isEnteredManually) groupJidEt.setText(StringUtils.getLocalpartHintByString(s?.trim().toString()))
                if (!s.isNullOrEmpty() && !groupJidEt.text.isNullOrEmpty() && getAccount() != null){
                    listenerActivity?.toolbarSetEnabled(true)
                } else listenerActivity?.toolbarSetEnabled(false)
            }
        })

        if (isIncognito){
            groupNameEt.hint = getString(R.string.groupchat_incognito_group)
        } else groupNameEt.hint = getString(R.string.groupchat_public_group)

    }

    private fun setupGroupJidEt(){
        groupJidEt.hint = if (isIncognito) "incognito-group" else "public-group"

        groupJidEt.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && !groupNameEt.text.isNullOrEmpty() && getAccount() != null) {
                    listenerActivity?.toolbarSetEnabled(true)
                } else listenerActivity?.toolbarSetEnabled(false)


                if (s.isNullOrEmpty()){
                    groupJidEt.hint = if (isIncognito) "incognito-group" else "public-group"
                    if (groupJidEt.hasFocus())
                        isEnteredManually = false
                } else {
                    groupJidEt.hint = ""
                    if (groupJidEt.hasFocus())
                    isEnteredManually = true
                }

            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupMembership() {
        membershipRg.check(R.id.group_membership_members_only_rb)
    }

    private fun setupIndex() {
        indexRg.check(R.id.group_index_none_rb)
    }

    private fun colorizeEverything(accountJid: AccountJid?){
        if (accountJid == null) return

        val color = ColorManager.getInstance().accountPainter.getAccountSendButtonColor(accountJid)
        val defaultLabelTextColor =
                if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.dark)
                    ColorManager.getColorWithAlpha(Color.GRAY, 0.5f)
                else ColorManager.getColorWithAlpha(Color.GRAY, 0.9f)
        val defaultLineBackgroundColor =
                if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.dark)
                    ColorManager.getColorWithAlpha(Color.GRAY, 0.5f)
                else ColorManager.getColorWithAlpha(Color.GRAY, 0.9f)

        fun colorizeEt(editText: EditText, textView: TextView, view: View){
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus){
                    textView.setTextColor(color)
                    view.setBackgroundColor(color)
                } else {
                    textView.setTextColor(defaultLabelTextColor)
                    view.setBackgroundColor(defaultLineBackgroundColor)
                }
            }
            if (editText.isFocused){
                textView.setTextColor(color)
                view.setBackgroundColor(color)
            } else {
                textView.setTextColor(defaultLabelTextColor)
                view.setBackgroundColor(defaultLabelTextColor)
            }
        }

        fun colorizeRb(textView: TextView, radioGroup: RadioGroup){
            textView.setTextColor(color)
            for (iterator in 0 until radioGroup.childCount){
                if (radioGroup.getChildAt(iterator) is RadioButton && Build.VERSION.SDK_INT >= 21){
                    (radioGroup.getChildAt(iterator) as RadioButton).buttonTintList = ColorStateList(
                                arrayOf(intArrayOf(-android.R.attr.state_checked),
                                        intArrayOf(android.R.attr.state_checked)),
                                intArrayOf( Color.GRAY, color))
                }
            }
        }

        colorizeEt(groupNameEt, groupNameTv, groupNameVw)
        colorizeEt(groupJidEt, groupJidTv, groupjidVw)
        colorizeEt(groupDescriptionEt, groupDescriptionTv, groupDescriptionVw)
        colorizeRb(membershipTv, membershipRg)
        colorizeRb(indexTv, indexRg)
    }

    override fun onSelected(accountJid: AccountJid) {
        if (listenerActivity != null){
            listenerActivity?.onAccountSelected(accountJid)
        }

        colorizeEverything(accountJid)
        if (accountJid != getAccount()){
            setAccount(accountJid)
            setAccountCircles()
            updateCircles()
        }
        circlesLayout.visibility = View.VISIBLE

        val serversList = GroupchatManager.getInstance().getAvailableGroupchatServersForAccountJid(accountJid)
        if (serversList != null && serversList.isNotEmpty()){
            serverTv.text = "\u200A@\u200A${serversList.first()}"
        } else {
            serverTv.text = "\u200A@\u200Agc.xabber.com"
        }

        isAccountSelected = true
        if (!groupJidEt.text.isNullOrEmpty() && !groupNameEt.text.isNullOrEmpty())
            listenerActivity?.toolbarSetEnabled(true)
    }

    private fun createListOfServers(): List<String> {
        val list = mutableListOf<String>()

        if (AccountManager.getInstance().enabledAccounts.size <= 1) {

            val serversList = GroupchatManager.getInstance()
                    .getAvailableGroupchatServersForAccountJid(AccountManager.getInstance().firstAccount)

            if (serversList != null && serversList.isNotEmpty())
                for (jid in serversList)
                    list.add(jid.toString())

            list.addAll(GroupchatManager.getInstance().getCustomGroupServers(AccountManager.getInstance().firstAccount))

        } else if (accountSpinner.selected != null) {
            for (jid in GroupchatManager.getInstance().getAvailableGroupchatServersForAccountJid(accountSpinner.selected))
                list.add(jid.toString())
            list.addAll(GroupchatManager.getInstance().getCustomGroupServers(accountSpinner.selected))
        }

        if (list.size == 0 )
            list.add("gc.xabber.com")
        list.add(getString(R.string.groupchat_custom_server))

        return list
    }

    private fun openServerDialog(){
        AlertDialog.Builder(context!!).apply {
            setItems(createListOfServers().toTypedArray()) { _, which ->
                if (which == createListOfServers().size-1){
                    openAddCustomServerDialog()
                } else serverTv.text = "\u200A@\u200A${createListOfServers()[which]}"
            }
        }.create().show()
    }

    private fun openAddCustomServerDialog(){
        val dialog = AlertDialog.Builder(context!!).apply {
            setTitle(getString(R.string.groupchat_custom_group_server))
            val editText = EditText(activity?.baseContext)
            val linearLayout = LinearLayoutCompat(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayoutCompat.VERTICAL
                setPadding(64, 12, 64, 12)
                addView(editText)
            }

            setView(linearLayout)
            setPositiveButton(getString(R.string.add)) { _, _ ->
                serverTv.text = "\u200A@\u200A${editText.text}"
                GroupchatManager.getInstance()
                        .saveCustomGroupServer(accountSpinner.selected ?: account, editText.text.toString())
            }
            setNegativeButton(R.string.cancel) { _, _ ->  }
        }
        dialog.create().show()
    }

    private fun setupServerTv(){
        serverTv.apply {
            setOnClickListener { openServerDialog() }
            if (AccountManager.getInstance().enabledAccounts.size == 1){
                val servers = GroupchatManager.getInstance().getAvailableGroupchatServersForAccountJid(
                        AccountManager.getInstance().firstAccount)
                if (servers != null && servers.size != 0){
                    text = "\u200A@\u200A${servers.first()}"
                    return@apply
                }
                val customServers = GroupchatManager.getInstance().getCustomGroupServers(
                        AccountManager.getInstance().firstAccount)
                if (customServers != null && customServers.size != 0)
                    text = "\u200A@\u200A${customServers.first()}"
            }
        }
        serverIv.setOnClickListener { openServerDialog() }
    }

    fun createGroupchat() {

        if (!isLocalpartCorrect(groupJidEt.text.toString())) return

        val membershipType = when (membershipRg.checkedRadioButtonId){
           R.id.group_membership_members_only_rb -> GroupchatMembershipType.MEMBER_ONLY
           R.id.group_membership_open_rb -> GroupchatMembershipType.OPEN
           else -> GroupchatMembershipType.NONE
        }

        val indexType = when (indexRg.checkedRadioButtonId){
            R.id.group_index_global_rb -> GroupchatIndexType.GLOBAL
            R.id.group_index_local_rb -> GroupchatIndexType.LOCAL
            else -> GroupchatIndexType.NONE
        }

        val privacyType = if (isIncognito) GroupchatPrivacyType.INCOGNITO else GroupchatPrivacyType.PUBLIC

        val server = serverTv.text.substring(3 until serverTv.text.length)

        GroupchatManager.getInstance().sendCreateGroupchatRequest(accountSpinner.selected ?: getAccount(),
                server, groupNameEt.text.toString(), groupDescriptionEt.text.toString(),
                groupJidEt.text.toString(), membershipType, indexType, privacyType, this)

        //todo working with circles
    }

    override fun onSuccessfullyCreated(accountJid: AccountJid?, contactJid: ContactJid?) {
        Application.getInstance().runOnUiThread {
            if (activity is CreateGroupActivity)
                activity?.startActivity(ChatActivity.createSendIntent(context, accountJid, contactJid, null))
        }
    }

    private fun isLocalpartCorrect(localpart: String): Boolean{
        //Invalid when localPart is NOT empty, and HAS "." at the start/end
        if (localpart[localpart.length - 1] == '.' || localpart[0] == '.') {
            showErrorBelowJidEt(getString(R.string.groupchat_invalid_group_id) + getString(R.string.INCORRECT_USER_NAME_ADDENDUM_LOCAL))
            return false
        }
        //Invalid when localPart is NOT empty, and contains ":" or "/" symbol. Other restricted localPart symbols get checked during the creation of the jid/userJid.
        if (localpart.contains(":")) {
            showErrorBelowJidEt(getString(R.string.groupchat_invalid_group_id) + String.format(getString(R.string
                    .INCORRECT_USER_NAME_ADDENDUM_LOCAL_SYMBOL), ":"))
            return false
        }

        //Invalid when localPart is NOT empty, and has multiple dots in a row
        if (localpart.contains("..")) {
            showErrorBelowJidEt(getString(R.string.groupchat_invalid_group_id) + getString(R.string.INCORRECT_USER_NAME_ADDENDUM_LOCAL))
            return false
        }
        if (localpart.isEmpty()){
            showErrorBelowJidEt(getString(R.string.groupchat_invalid_group_id) + getString(R.string.empty_field))
            return false
        }
        return true
    }

    private fun showErrorBelowJidEt(stringError: String){
        errorTv.text = stringError
        errorTv.visibility = if ("" == stringError) View.INVISIBLE else View.VISIBLE
        groupJidEt.requestFocus()
    }

    override fun onJidConflict() {
        Application.getInstance().runOnUiThread {
            showErrorBelowJidEt(requireContext().getString(R.string.groupchat_jid_already_exists))
            listenerActivity?.showProgress(false)
            listenerActivity?.toolbarSetEnabled(false)
        }
    }

    override fun onOtherError() {
        Application.getInstance().runOnUiThread {
            Toast.makeText(context,
                    getString(R.string.groupchat_failed_to_create_groupchat_with_unknown_reason),
                    Toast.LENGTH_LONG).show()
            listenerActivity?.showProgress(false)
            listenerActivity?.toolbarSetEnabled(false)
        }
    }

    interface Listener {
        fun onAccountSelected(account: AccountJid?)
        fun showProgress(show: Boolean)
        fun toolbarSetEnabled(enabled: Boolean)
    }

    companion object {

        private val LOG_TAG = this::class.java.simpleName.toString()

        private const val ARGUMENT_IS_INCOGNITO = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.ARGUMENT_IS_INCOGNITO"

        private const val SAVED_ACCOUNT = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_ACCOUNT"
        private const val SAVED_GROUP_NAME = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_GROUP_NAME"
        private const val SAVED_GROUP_JID = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_GROUP_JID"
        private const val SAVED_GROUP_SERVER = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_GROUP_SERVER"
        private const val SAVED_GROUP_DESCRIPTION = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_GROUP_DESCRIPTION"
        private const val SAVED_ERROR = "com.xabber.android.ui.fragment.groups..CreateGroupFragment.SAVED_ERROR"

        @JvmStatic
        fun newInstance(isIncognito: Boolean) = CreateGroupFragment().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_IS_INCOGNITO, isIncognito) }
        }

    }

}