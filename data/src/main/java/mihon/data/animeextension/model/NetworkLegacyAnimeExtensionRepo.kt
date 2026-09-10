package mihon.data.animeextension.model

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.domain.extension.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkLegacyAnimeExtensionRepo(
    // Nullable is not enough for kotlinx.serialization: without a default the field is
    // still required, and Aniyomi's repo.json carries neither of these. Mihon's own
    // repositories do, which is why its manga side never hit this.
    @SerialName("index_v2")
    val indexV2: String? = null,
    val meta: Meta,
) : BaseNetworkAnimeExtensionStore {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String? = null,
        val website: String,
        val signingKeyFingerprint: String,
    )

    override fun toExtensionStore(indexUrl: String): ExtensionStore {
        return ExtensionStore(
            indexUrl = indexUrl,
            name = meta.name,
            badgeLabel = meta.shortName ?: meta.name,
            signingKey = meta.signingKeyFingerprint,
            contact = ExtensionStore.Contact(
                website = meta.website,
                discord = null,
            ),
            isLegacy = true,
            extensionListUrl = null,
        )
    }
}
