package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.Product
import com.transad.app.databinding.ItemLabelingProductBinding

class LabelingProductPickerAdapter(
    private val onProductSelected: (Product) -> Unit
) : ListAdapter<Product, LabelingProductPickerAdapter.ViewHolder>(DiffCallback()) {

    var selectedProductId: Int? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLabelingProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onProductSelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), selectedProductId)
    }

    class ViewHolder(
        private val binding: ItemLabelingProductBinding,
        private val onProductSelected: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundProduct: Product? = null

        init {
            binding.root.setOnClickListener { boundProduct?.let(onProductSelected) }
        }

        fun bind(product: Product, selectedProductId: Int?) {
            boundProduct = product
            val ctx = binding.root.context
            val selected = product.id == selectedProductId && selectedProductId != null

            binding.tvReference.text = ctx.getString(
                R.string.labeling_product_reference,
                product.displayReference()
            )
            binding.tvName.text = product.displayTitle()
            binding.chipSelected.isVisible = selected

            val metaParts = buildList {
                product.brandName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(ctx.getString(R.string.labeling_product_brand, it))
                }
                product.configurationName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(ctx.getString(R.string.labeling_product_configuration, it))
                }
                add(ctx.getString(R.string.inventory_stock, product.stockQuantity))
            }
            binding.tvMeta.text = metaParts.joinToString(" · ")

            val strokeColor = if (selected) R.color.transad_primary else android.R.color.transparent
            binding.cardProduct.strokeWidth = if (selected) (2 * ctx.resources.displayMetrics.density).toInt() else 0
            binding.cardProduct.strokeColor = ContextCompat.getColor(ctx, strokeColor)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(old: Product, new: Product) = old.id == new.id
        override fun areContentsTheSame(old: Product, new: Product) = old == new
    }
}
