package me.devsaki.hentoid.viewholders

import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.Site

class DrawerItem(
    val label: String,
    val icon: Int,
    val activityClass: Class<out AppCompatActivity>,
    uniqueId: Long
) :
    AbstractItem<DrawerItem.ViewHolder>() {

    var site: Site? = null
    var flagNew = false
    var alertStatus = AlertStatus.NONE

    init {
        identifier = uniqueId
    }

    override val type: Int get() = R.id.drawer

    override val layoutRes: Int get() = R.layout.item_drawer

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<DrawerItem>(view) {
        private val icon: ImageView = itemView.findViewById(R.id.drawer_item_icon)
        private val alert: ImageView = itemView.findViewById(R.id.drawer_item_alert)
        private val title: TextView = itemView.findViewById(R.id.drawer_item_txt)

        override fun bindView(item: DrawerItem, payloads: List<Any>) {
            icon.setImageResource(item.icon)
            if (item.alertStatus != AlertStatus.NONE) {
                alert.visibility = View.VISIBLE
                alert.setColorFilter(
                    ContextCompat.getColor(alert.context, item.alertStatus.color),
                    PorterDuff.Mode.SRC_IN
                )
            } else alert.visibility = View.GONE
            title.text = String.format("%s%s", item.label, if (item.flagNew) " *" else "")
        }

        override fun unbindView(item: DrawerItem) {
            // Nothing special here
        }
    }

    companion object {
        fun fromSite(site: Site): DrawerItem {
            val result = DrawerItem(
                site.description.uppercase(),
                site.ico,
                Content.getWebActivityClass(site),
                site.code.toLong()
            )
            result.site = site
            return result
        }
    }
}