package com.prishvindt.sector.updates

class UpdateChecker(
    private val repository: UpdateRepository
) {
    suspend fun check(): Result<UpdateInfo?> = repository.checkForUpdate()
}
