package com.wkq.localsignage.monetization

import androidx.lifecycle.ViewModel

class BillingViewModel : ViewModel() {
    val uiState = MonetizationRepository.uiState

    fun refresh() {
        MonetizationRepository.refresh(loadCatalog = true)
    }
}
