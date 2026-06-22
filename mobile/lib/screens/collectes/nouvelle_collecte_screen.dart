import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/client.dart';
import '../../core/models/collecte_locale.dart';
import '../../core/services/client_service.dart';
import '../../core/services/sync_service.dart';

String _generateUuid() {
  final r = Random.secure();
  String hex(int n) => r.nextInt(n).toRadixString(16).padLeft(2, '0');
  return '${hex(256)}${hex(256)}-${hex(256)}-4${hex(16).substring(1)}'
      '-${(8 + r.nextInt(4)).toRadixString(16)}${hex(16).substring(1)}'
      '-${hex(256)}${hex(256)}${hex(256)}';
}

class NouvelleCollecteScreen extends StatefulWidget {
  const NouvelleCollecteScreen({super.key});

  @override
  State<NouvelleCollecteScreen> createState() => _NouvelleCollecteScreenState();
}

class _NouvelleCollecteScreenState extends State<NouvelleCollecteScreen> {
  final _searchController = TextEditingController();
  final _amountController = TextEditingController(text: '0');

  String _canal = 'ESPECES';
  bool _gpsEnabled = true;
  bool _submitting = false;

  Client? _selectedClient;
  List<Client> _searchResults = [];
  bool _searching = false;

  static const _canaux = ['ESPECES', 'MTN', 'ORANGE', 'WAVE'];

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    _amountController.dispose();
    super.dispose();
  }

  void _onSearchChanged() {
    final q = _searchController.text.trim();
    if (q.length < 2) {
      if (mounted) setState(() => _searchResults = []);
      return;
    }
    _doSearch(q);
  }

  Future<void> _doSearch(String q) async {
    if (!mounted) return;
    setState(() => _searching = true);
    try {
      final clients = await context.read<ClientService>().searchClients(search: q);
      if (mounted) setState(() { _searchResults = clients; _searching = false; });
    } catch (_) {
      if (mounted) setState(() { _searchResults = []; _searching = false; });
    }
  }

  void _selectClient(Client c) {
    setState(() {
      _selectedClient = c;
      _searchResults = [];
      _searchController.clear();
    });
  }

  Future<void> _onSubmit() async {
    if (_selectedClient == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Veuillez sélectionner un client')));
      return;
    }
    final amount = double.tryParse(_amountController.text.replaceAll(RegExp(r'\s'), '')) ?? 0;
    if (amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Montant invalide')));
      return;
    }

    setState(() => _submitting = true);
    try {
      final now = DateTime.now();
      final collecte = CollecteLocale(
        uuidMobile: _generateUuid(),
        clientIdExterne: _selectedClient!.idClient.toString(),
        montantCollecte: amount,
        dateCollecte: DateFormat('yyyy-MM-dd').format(now),
        canalPaiement: _canal,
        createdAt: now,
      );

      await context.read<SyncService>().ajouterCollecteLocale(collecte);

      if (!mounted) return;
      setState(() => _submitting = false);
      context.go('/collectes/confirmation', extra: collecte);
    } catch (e) {
      if (mounted) {
        setState(() => _submitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur : ${e.toString()}')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      body: Column(
        children: [
          _buildTopBar(context),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _buildSearchField(),
                if (_searching)
                  const Padding(padding: EdgeInsets.all(10),
                    child: Center(child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.teal))),
                if (_searchResults.isNotEmpty && _selectedClient == null)
                  _buildSearchResults(),
                const SizedBox(height: 14),
                if (_selectedClient != null) ...[
                  _buildClientCard(),
                  const SizedBox(height: 20),
                ],
                _buildAmountInput(),
                const SizedBox(height: 20),
                _buildCanalSelector(),
                const SizedBox(height: 20),
                _buildGpsRow(),
                const SizedBox(height: 32),
                _buildSubmitButton(),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTopBar(BuildContext context) {
    return Container(
      color: AppColors.navyDark,
      padding: EdgeInsets.only(top: MediaQuery.of(context).padding.top + 8, bottom: 12, left: 4, right: 16),
      child: Row(
        children: [
          IconButton(icon: const Icon(Icons.arrow_back_rounded, color: Colors.white), onPressed: () => Navigator.of(context).pop()),
          const Expanded(
            child: Text('Enregistrer une collecte',
              style: TextStyle(fontFamily: 'Inter', fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchField() {
    return Container(
      decoration: BoxDecoration(color: AppColors.darkSurface, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.darkBorder)),
      child: TextField(
        controller: _searchController,
        style: const TextStyle(fontFamily: 'Inter', fontSize: 14, color: Colors.white),
        decoration: const InputDecoration(
          hintText: 'Rechercher un client (nom, téléphone…)',
          hintStyle: TextStyle(fontFamily: 'Inter', fontSize: 14, color: AppColors.textSecondary),
          prefixIcon: Icon(Icons.search_rounded, color: AppColors.textSecondary, size: 20),
          border: InputBorder.none,
          contentPadding: EdgeInsets.symmetric(vertical: 14),
        ),
      ),
    );
  }

  Widget _buildSearchResults() {
    return Container(
      margin: const EdgeInsets.only(top: 4),
      decoration: BoxDecoration(color: AppColors.darkSurface, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.darkBorder)),
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _searchResults.length.clamp(0, 5),
        separatorBuilder: (_, __) => Divider(height: 1, color: AppColors.darkBorder),
        itemBuilder: (_, i) {
          final c = _searchResults[i];
          return ListTile(
            dense: true,
            leading: CircleAvatar(
              backgroundColor: AppColors.teal.withOpacity(0.15),
              child: Text(c.initials, style: const TextStyle(color: AppColors.teal, fontWeight: FontWeight.bold, fontSize: 13)),
            ),
            title: Text(c.fullName, style: const TextStyle(color: Colors.white, fontFamily: 'Inter', fontSize: 13)),
            subtitle: c.telephone != null
              ? Text(c.telephone!, style: const TextStyle(color: AppColors.textSecondary, fontSize: 11)) : null,
            onTap: () => _selectClient(c),
          );
        },
      ),
    );
  }

  Widget _buildClientCard() {
    final c = _selectedClient!;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: AppColors.darkSurface, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.teal.withOpacity(0.5))),
      child: Row(
        children: [
          CircleAvatar(backgroundColor: AppColors.teal.withOpacity(0.15),
            child: Text(c.initials, style: const TextStyle(color: AppColors.teal, fontWeight: FontWeight.bold))),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(c.fullName, style: const TextStyle(fontFamily: 'Inter', fontSize: 15, fontWeight: FontWeight.w700, color: Colors.white)),
                if (c.telephone != null)
                  Text(c.telephone!, style: const TextStyle(fontFamily: 'Inter', fontSize: 12, color: AppColors.textSecondary)),
              ],
            ),
          ),
          TextButton(
            onPressed: () => setState(() { _selectedClient = null; }),
            style: TextButton.styleFrom(foregroundColor: AppColors.teal),
            child: const Text('Changer', style: TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }

  Widget _buildAmountInput() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 20),
      decoration: BoxDecoration(color: AppColors.darkSurface, borderRadius: BorderRadius.circular(16), border: Border.all(color: AppColors.darkBorder)),
      child: Column(
        children: [
          const Text('Montant', style: TextStyle(fontFamily: 'Inter', fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
          const SizedBox(height: 8),
          TextField(
            controller: _amountController,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            textAlign: TextAlign.center,
            style: const TextStyle(fontFamily: 'Inter', fontSize: 52, fontWeight: FontWeight.w800, color: Colors.white),
            decoration: const InputDecoration(border: InputBorder.none, isDense: true, contentPadding: EdgeInsets.zero),
          ),
          const SizedBox(height: 4),
          const Text('FCFA', style: TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
        ],
      ),
    );
  }

  Widget _buildCanalSelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text("Canal de paiement", style: TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white)),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          children: _canaux.map((canal) {
            final sel = _canal == canal;
            return GestureDetector(
              onTap: () => setState(() => _canal = canal),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                decoration: BoxDecoration(
                  color: sel ? AppColors.teal.withOpacity(0.14) : AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: sel ? AppColors.teal : AppColors.darkBorder, width: sel ? 1.5 : 1),
                ),
                child: Text(canal, style: TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: sel ? FontWeight.w700 : FontWeight.w500, color: sel ? AppColors.teal : AppColors.textSecondary)),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildGpsRow() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: AppColors.darkSurface, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.darkBorder)),
      child: Row(
        children: [
          Icon(Icons.location_on_rounded, color: _gpsEnabled ? AppColors.teal : AppColors.textSecondary, size: 20),
          const SizedBox(width: 10),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Position GPS jointe', style: TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
                SizedBox(height: 2),
                Text("Traçabilité terrain (Loi 2024/017)", style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: AppColors.textSecondary)),
              ],
            ),
          ),
          Switch(value: _gpsEnabled, onChanged: (v) => setState(() => _gpsEnabled = v), activeColor: AppColors.teal, activeTrackColor: AppColors.teal.withOpacity(0.3)),
        ],
      ),
    );
  }

  Widget _buildSubmitButton() {
    return SizedBox(
      width: double.infinity, height: 52,
      child: ElevatedButton(
        onPressed: _submitting ? null : _onSubmit,
        style: ElevatedButton.styleFrom(backgroundColor: AppColors.teal, foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)), elevation: 0),
        child: _submitting
          ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
          : const Text('Enregistrer la collecte', style: TextStyle(fontFamily: 'Inter', fontSize: 16, fontWeight: FontWeight.w700)),
      ),
    );
  }
}
