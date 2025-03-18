package me.devsaki.hentoid.database

class ObjectBoxDAOContainer {
    private var _dao = ObjectBoxDAO()

    fun reset() {
        _dao.cleanup()
        _dao = ObjectBoxDAO()
    }

    val dao: CollectionDAO
        get() = _dao
}