package nep.timeline.cirno.ui.utils

import com.topjohnwu.superuser.Shell
import java.util.LinkedHashSet
import java.util.regex.Pattern

object RootPackageRepository {
    private val userPattern = Pattern.compile("UserInfo\\{(\\d+):")

    fun getManagedAppKeySet(): Set<String> {
        val result = LinkedHashSet<String>()
        for (userId in getInstalledUserIdsByRoot()) {
            for (pkg in getInstalledPackagesForUserByRoot(userId)) {
                result.add("$pkg#$userId")
            }
        }
        return result
    }

    /** Unlike the legacy helper, a failed query must not look like an empty inventory. */
    fun loadManagedAppKeySet(): Set<String> {
        val users = LinkedHashSet<Int>()
        for (line in query("pm list users", "cmd user list")) {
            val matcher = userPattern.matcher(line)
            if (matcher.find()) matcher.group(1)?.toIntOrNull()?.let(users::add)
        }
        check(users.isNotEmpty()) { "Failed to enumerate Android users" }
        return buildSet {
            for (userId in users) {
                for (line in query("pm list packages --user $userId", "cmd package list packages --user $userId")) {
                    if (line.startsWith("package:")) {
                        val pkg = line.removePrefix("package:").trim()
                        if (pkg.isNotEmpty()) add("$pkg#$userId")
                    }
                }
            }
        }
    }

    private fun query(primary: String, fallback: String): List<String> {
        val first = Shell.cmd(primary).exec()
        if (first.isSuccess) return first.out
        val second = Shell.cmd(fallback).exec()
        check(second.isSuccess) { "Package query failed: $primary" }
        return second.out
    }

    private fun getInstalledUserIdsByRoot(): List<Int> {
        val userIds = LinkedHashSet<Int>()
        var lines = runRootCommand("pm list users")
        if (lines.isEmpty()) {
            lines = runRootCommand("cmd user list")
        }
        for (line in lines) {
            val matcher = userPattern.matcher(line)
            if (matcher.find()) {
                matcher.group(1)?.toIntOrNull()?.let(userIds::add)
            }
        }
        if (userIds.isEmpty()) {
            userIds.add(0)
        }
        return userIds.toList()
    }

    private fun getInstalledPackagesForUserByRoot(userId: Int): List<String> {
        val packages = LinkedHashSet<String>()
        var lines = runRootCommand("pm list packages --user $userId")
        if (lines.isEmpty()) {
            lines = runRootCommand("cmd package list packages --user $userId")
        }
        for (line in lines) {
            if (!line.startsWith("package:")) continue
            val pkg = line.removePrefix("package:").trim()
            if (pkg.isNotEmpty()) {
                packages.add(pkg)
            }
        }
        return packages.toList()
    }

    private fun runRootCommand(command: String): List<String> {
        return try {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) result.out else emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
