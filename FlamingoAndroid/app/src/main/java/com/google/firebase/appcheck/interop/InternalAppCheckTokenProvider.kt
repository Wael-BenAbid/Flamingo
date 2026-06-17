package com.google.firebase.appcheck.interop

// Minimal shim to avoid NoClassDefFoundError during Firebase initialization
// The real implementation is provided by the Firebase App Check interop artifact.
class InternalAppCheckTokenProvider
