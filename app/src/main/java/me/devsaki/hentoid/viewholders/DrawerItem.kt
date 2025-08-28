package me.devsaki.hentoid.viewholders

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.Site

class DrawerItem<T>(
    val label: String,
    val icon: Int,
    uniqueId: Long,
    val italicFont: Boolean = false,
    private val mTag: T? = null
) :
    AbstractItem<DrawerItem.ViewHolder<T>>() {

    var site: Site? = null
    var flagNew = false
    var alertStatus = AlertStatus.NONE

    init {
        identifier = uniqueId
    }

    fun getObject(): T? {
        return mTag
    }

    override val type: Int get() = R.id.drawer

    override val layoutRes: Int get() = R.layout.item_drawer

    override fun getViewHolder(v: View): ViewHolder<T> {
        return ViewHolder(v)
    }

    class ViewHolder<T>(view: View) : FastAdapter.ViewHolder<DrawerItem<T>>(view) {
        private val icon: ImageView = itemView.findViewById(R.id.drawer_item_icon)
        private val alert: ImageView = itemView.findViewById(R.id.drawer_item_alert)
        private val title: TextView = itemView.findViewById(R.id.drawer_item_txt)

        override fun bindView(item: DrawerItem<T>, payloads: List<Any>) {
            icon.setImageResource(item.icon)
            if (item.alertStatus != AlertStatus.NONE) {
                alert.visibility = View.VISIBLE
                alert.setColorFilter(
                    ContextCompat.getColor(alert.context, item.alertStatus.color),
                    PorterDuff.Mode.SRC_IN
                )
            } else alert.visibility = View.GONE
            title.text = String.format("%s%s", item.label, if (item.flagNew) " *" else "")

            if (item.italicFont) title.setTypeface(null, Typeface.ITALIC)
            else title.setTypeface(null, Typeface.NORMAL)
        }

        override fun unbindView(item: DrawerItem<T>) {
            // Nothing special here
        }
    }

    companion object {
        fun fromSite(site: Site): DrawerItem<Site> {
            val result = DrawerItem(
                site.description.uppercase(),
                site.ico,
                site.code.toLong(),
                mTag = site
            )
            result.site = site
            return result
        }
    }
}