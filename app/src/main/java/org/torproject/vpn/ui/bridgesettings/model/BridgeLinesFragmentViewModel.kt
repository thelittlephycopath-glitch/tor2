package org.torproject.vpn.ui.bridgesettings.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.torproject.vpn.R
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType
import org.torproject.vpn.utils.bridgeType
import org.torproject.vpn.utils.getBridgeTypeLabel
import org.torproject.vpn.utils.isValidBridgeLine
import org.torproject.vpn.vpn.VpnServiceCommand
import org.torproject.vpn.vpn.VpnStatusObservable

class BridgeLinesFragmentViewModel(application: Application) : AndroidViewModel(application) {

    val preferenceHelper = PreferenceHelper(application)

    private val _bridgeLines = MutableLiveData(preferenceHelper.bridgeLines.distinct())
    val bridgeLines: LiveData<List<String>> get() = _bridgeLines

    val helperText: StateFlow<String> = bridgeLines.asFlow().map { bridgeLines ->
        if (bridgeLines.isEmpty()) {
            return@map application.resources.getString(R.string.action_paste_bridges)
        }
        val validationResults = bridgeLines.mapIndexed { index, line ->
            Triple(index + 1, line.isValidBridgeLine(), line.bridgeType())
        }

        val validCount = validationResults.count { it.second }
        val invalidCount = validationResults.count { !it.second }
        if (invalidCount > 0) {
            val firstInvalidIndex = validationResults.first { !it.second }.first
            return@map application.resources.getQuantityString(
                R.plurals.n_invalid_bridges,
                invalidCount,
                invalidCount,
                firstInvalidIndex
            )
        }
        // count unique types
        val uniqueTypes = validationResults.map { it.third }.toSet()
        if (uniqueTypes.size > 1) {
            return@map application.resources.getQuantityString(
                R.plurals.n_bridges,
                validCount,
                validCount
            )
        }
        return@map application.resources.getQuantityString(
            R.plurals.n_bridges,
            validCount,
            "$validCount ${getBridgeTypeLabel(application, uniqueTypes.first())}".trim()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    fun addBridgeLine(value: String): Boolean {
        val list = _bridgeLines.value?.toMutableList() ?: mutableListOf()
        if (list.contains(value)) return false
        list.add(value)
        _bridgeLines.value = list
        return true
    }

    fun removeBridgeLine(value: String) {
        val list = _bridgeLines.value?.toMutableList() ?: emptyList<String>().toMutableList()
        list.remove(value)
        _bridgeLines.value = list
    }

    fun save(): Boolean {
        val bridgeLineList = _bridgeLines.value ?: emptyList()
        val bridgeLineValidationResult = bridgeLineList.map { it.isValidBridgeLine() }
        if (bridgeLineValidationResult.any { !it }) {
            // don't save if there are invalid lines
            return false
        }
        preferenceHelper.bridgeLines = bridgeLineList
        if (bridgeLineList.isEmpty()) {
            preferenceHelper.bridgeType = BridgeType.None
        } else {
            preferenceHelper.bridgeType = BridgeType.Manual
            updateVPNSettings()
        }
        return true
    }

    private fun updateVPNSettings() {
        if (VpnStatusObservable.isVPNActive()) {
            VpnServiceCommand.startVpn(getApplication())
        }
    }
}
