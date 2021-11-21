package me.devsaki.hentoid.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.IItem

interface INestedItem<VH : RecyclerView.ViewHolder> : IItem<VH> {
    fun getLevel(): Int
}