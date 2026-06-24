import 'package:flutter/foundation.dart';

// FCM stub — activation en 3 étapes (voir README FCM) :
// 1. Créer un projet Firebase Console → télécharger google-services.json → android/app/
// 2. settings.gradle: id "com.google.gms.google-services" version "4.4.2" apply false
//    app/build.gradle:  id "com.google.gms.google-services"
// 3. pubspec.yaml: firebase_core, firebase_messaging, flutter_local_notifications
// 4. Décommenter le corps de initialize() et les imports firebase_*

class NotificationService {
  NotificationService();

  Future<void> initialize() async {
    debugPrint('[FCM] Stub — Firebase non configuré. Voir notification_service.dart.');
  }
}
