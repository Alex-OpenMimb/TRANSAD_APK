package com.transad.app

import com.transad.app.api.Product
import com.transad.app.api.ProductAttribute
import java.util.Locale

data class LabelingSelection(
    val tireProduct: Product,
    val labelProduct: Product
)

fun Product.isLabelProduct(): Boolean {
    val type = typeName().lowercase(Locale.getDefault())
    if (type.contains("etiqueta")) return true
    val n = name?.lowercase(Locale.getDefault()).orEmpty()
    return n.contains("etiqueta") || n.contains("uhf") || n.contains("tire tag") ||
        n.contains("chipset") || (n.contains("tag") && n.contains("tire"))
}

fun Product.isTireProduct(): Boolean {
    val type = typeName().lowercase(Locale.getDefault())
    if (type.contains("llanta")) return true
    return name?.lowercase(Locale.getDefault())?.contains("llanta") == true
}

fun Product.labelingAttributes(): List<ProductAttribute> {
    val forLabeling = attributes.filter { attr ->
        attr.contexts.any { it.equals("labeling", ignoreCase = true) }
    }
    if (forLabeling.isNotEmpty()) return forLabeling
    return attributes.filter { attr ->
        attr.contexts.none { it.equals("inspection", ignoreCase = true) }
    }
}

fun Product.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase(Locale.getDefault())
    return listOfNotNull(reference, name, idErp, brandName, configurationName, typeName())
        .any { it.lowercase(Locale.getDefault()).contains(q) }
}

fun Product.displayReference(): String = reference?.trim().orEmpty().ifBlank { "—" }

fun Product.displayTitle(): String = name?.trim().orEmpty().ifBlank { displayReference() }
