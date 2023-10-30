package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Achievement

class AchievementItem(
    val achievement: Achievement,
    val enabled: Boolean
) : AbstractItem<AchievementItem.ViewHolder>() {

    init {
        identifier = achievement.id.toLong()
    }

    override val type: Int get() = R.id.achievement

    override val layoutRes: Int get() = R.layout.item_achievement

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AchievementItem>(view) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val desc: TextView = itemView.findViewById(R.id.description)

        override fun bindView(item: AchievementItem, payloads: List<Any>) {
            val ctx = icon.context
            val iconColor =
                if (!item.enabled) R.color.dark_gray else Achievement.colorFromType(item.achievement.type)
            icon.setImageResource(item.achievement.icon)
            icon.setColorFilter(ContextCompat.getColor(ctx, iconColor))
            val textColor =
                if (!item.enabled) R.color.dark_gray else R.color.white_opacity_87
            title.text = ctx.resources.getText(item.achievement.title)
            title.setTextColor(ContextCompat.getColor(ctx, textColor))
            val descRes = if (item.achievement.himitsu) R.string.what else item.achievement.description
            desc.text = ctx.resources.getText(descRes)
            desc.setTextColor(ContextCompat.getColor(ctx, textColor))
        }

        override fun unbindView(item: AchievementItem) {
            // Nothing special here
        }
    }
}