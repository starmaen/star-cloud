package com.example.ui

/** إجراءات تحتاج Activity (منتقيات، صلاحيات). */
class Actions(
    val pickFolder: () -> Unit,
    val signInGoogle: () -> Unit,
    val askContacts: () -> Unit,
    val askSms: () -> Unit,
    val askCalls: () -> Unit,
    val openAllFiles: () -> Unit,
    val requestShizuku: () -> Unit,
    val requestDefaultSms: () -> Unit,
    val importVcf: () -> Unit
)
