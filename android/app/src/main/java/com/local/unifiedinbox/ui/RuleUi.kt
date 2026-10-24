package com.local.unifiedinbox.ui

import com.local.unifiedinbox.R
import com.local.unifiedinbox.model.RuleType

fun RuleType.labelRes(): Int = when (this) {
    RuleType.GROUP -> R.string.rules_type_group
    RuleType.CONTACT -> R.string.rules_type_contact
    RuleType.KEYWORD -> R.string.rules_type_keyword
}
