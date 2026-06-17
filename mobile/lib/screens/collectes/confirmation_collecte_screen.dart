import 'package:flutter/material.dart';
import '../../core/models/collecte_locale.dart';

class ConfirmationCollecteScreen extends StatelessWidget {
  final CollecteLocale collecte;

  const ConfirmationCollecteScreen({super.key, required this.collecte});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Confirmation collecte')),
      body: const Center(child: Text('Confirmation — à implémenter')),
    );
  }
}
