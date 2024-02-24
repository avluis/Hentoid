package me.devsaki.hentoid.json.sources

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviantArtUser(
    val gruser: User,
    val owner: Owner
) {
    @JsonClass(generateAdapter = true)
    data class User(
        val page: UserPage
    )

    @JsonClass(generateAdapter = true)
    data class UserPage(
        val modules: List<PageModule>
    )

    @JsonClass(generateAdapter = true)
    data class PageModule(
        val moduleData: ModuleData
    )

    @JsonClass(generateAdapter = true)
    data class ModuleData(
        val dataKey: String,
        val folderDeviations: FolderDeviations?
    )

    @JsonClass(generateAdapter = true)
    data class FolderDeviations(
        val username: String,
        val folderId: Int
    )

    @JsonClass(generateAdapter = true)
    data class Owner(
        val username: String
    )

    fun getDeviationFolderId(): Int {
        gruser.page.modules.forEach {
            if (it.moduleData.dataKey == "folder_deviations" && it.moduleData.folderDeviations != null) {
                if (it.moduleData.folderDeviations.username == owner.username) return it.moduleData.folderDeviations.folderId
            }
        }
        return -1
    }
}