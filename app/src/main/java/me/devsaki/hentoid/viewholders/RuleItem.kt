package me.devsaki.hentoid.viewholders

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.RenamingRuleBundle
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.enums.AttributeType

class RuleItem(val rule: RenamingRule) :
    AbstractItem<RuleItem.ViewHolder>() {

    var attrType: AttributeType
    var source: String
    var target: String

    init {
        tag = rule
        attrType = rule.attributeType
        source = rule.sourceName
        target = rule.targetName
        identifier = rule.id
    }

    override val type: Int get() = R.id.renamingRule

    override val layoutRes: Int get() = R.layout.item_rule

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<RuleItem>(view) {
        private val typeDot: TextView = itemView.findViewById(R.id.colorDot)
        private val source: TextView = itemView.findViewById(R.id.source_txt)
        private val target: TextView = itemView.findViewById(R.id.target_txt)

        override fun bindView(item: RuleItem, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                val bundleParser = RenamingRuleBundle(payloads[0] as Bundle)
                val intValue = bundleParser.attrType
                if (intValue != null) item.attrType = AttributeType.searchByCode(intValue)!!
                var stringValue = bundleParser.source
                if (stringValue != null) item.source = stringValue
                stringValue = bundleParser.target
                if (stringValue != null) item.target = stringValue
            }
            typeDot.setTextColor(
                ContextCompat.getColor(
                    typeDot.context,
                    item.attrType.color
                )
            )
            source.text = item.source
            target.text = item.target
        }

        override fun unbindView(item: RuleItem) {
            // Nothing special here
        }
    }
}