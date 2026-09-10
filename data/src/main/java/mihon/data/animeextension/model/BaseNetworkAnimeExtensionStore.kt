package mihon.data.animeextension.model

import mihon.domain.extension.model.ExtensionStore

interface BaseNetworkAnimeExtensionStore {
    fun toExtensionStore(indexUrl: String): ExtensionStore
}
