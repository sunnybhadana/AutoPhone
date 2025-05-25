package com.revaltronics.autophone.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.revaltronics.commons.adapters.MyRecyclerViewAdapter
import com.revaltronics.commons.adapters.MyRecyclerViewListAdapter
import com.revaltronics.commons.extensions.getProperPrimaryColor
import com.revaltronics.commons.extensions.getProperTextColor
import com.revaltronics.commons.extensions.getTextSize
import com.revaltronics.commons.helpers.SORT_BY_FIRST_NAME
import com.revaltronics.commons.helpers.SORT_BY_SURNAME
import com.revaltronics.commons.views.MyRecyclerView
import com.revaltronics.autophone.activities.MainActivity
import com.revaltronics.autophone.activities.SimpleActivity
import com.revaltronics.autophone.adapters.ContactsAdapter
import com.revaltronics.autophone.adapters.RecentCallsAdapter
import com.revaltronics.autophone.databinding.FragmentLettersLayoutBinding
import com.revaltronics.autophone.databinding.FragmentRecentsBinding
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.helpers.Config

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected lateinit var innerBinding: BINDING
    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperPrimaryColor())
        }
    }

    fun startNameWithSurnameChanged(startNameWithSurname: Boolean) {
        if (this !is RecentsFragment) {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                config.sorting = if (startNameWithSurname) SORT_BY_SURNAME else SORT_BY_FIRST_NAME
                (this@MyViewPagerFragment.activity!! as MainActivity).refreshFragments()
            }
        }
    }

    fun finishActMode() {
        (innerBinding.fragmentList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (innerBinding.recentsList?.adapter as? MyRecyclerViewListAdapter<*>)?.finishActMode()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun fontSizeChanged() {
        if (this is RecentsFragment) {
            (innerBinding.recentsList.adapter as? RecentCallsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        } else {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        }
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)

    abstract fun myRecyclerView(): MyRecyclerView?

    interface InnerBinding {
        val fragmentList: MyRecyclerView?
        val recentsList: MyRecyclerView?
    }

    class LettersInnerBinding(val binding: FragmentLettersLayoutBinding) : InnerBinding {
        override val fragmentList: MyRecyclerView = binding.fragmentList
        override val recentsList = null
    }

    class RecentsInnerBinding(val binding: FragmentRecentsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = binding.recentsList
    }
}
