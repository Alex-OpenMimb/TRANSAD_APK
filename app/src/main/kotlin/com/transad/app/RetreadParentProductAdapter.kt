package com.transad.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import com.transad.app.api.Product
import com.transad.app.databinding.ItemRetreadParentProductBinding

/**
 * Adapter del selector "Producto original de la llanta" (dropdown no editable):
 * nombre en la línea principal, marca debajo más pequeña y configuración al final en gris.
 */
class RetreadParentProductAdapter(
    context: Context,
    private val products: List<Product>
) : ArrayAdapter<Product>(context, 0, products) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(getItem(position), convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(getItem(position), convertView, parent)

    private fun bind(product: Product?, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView != null) {
            ItemRetreadParentProductBinding.bind(convertView)
        } else {
            ItemRetreadParentProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }
        val ctx = binding.root.context
        binding.tvRetreadParentName.text = product?.name?.trim().orEmpty()
        binding.tvRetreadParentBrand.text = ctx.getString(
            R.string.labeling_product_brand,
            product?.brandName?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        )
        binding.tvRetreadParentConfiguration.text = ctx.getString(
            R.string.labeling_product_configuration,
            product?.configurationName?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        )
        return binding.root
    }

    /** No editable (inputType="none"): siempre muestra la lista completa, sin filtrar por texto. */
    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
            values = products
            count = products.size
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

        /**
         * Texto que AutoCompleteTextView pone en el campo al seleccionar un ítem.
         * Sin esto usa el `toString()` del data class `Product` completo.
         */
        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? Product)?.name?.trim().orEmpty()
    }
}
