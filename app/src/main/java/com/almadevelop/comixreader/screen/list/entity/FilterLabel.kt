package com.almadevelop.comixreader.screen.list.entity

import com.almadevelop.comixreader.logic.entity.query.filter.FilterGroup

data class FilterLabel(val groupId: FilterGroup.ID, val title: String)