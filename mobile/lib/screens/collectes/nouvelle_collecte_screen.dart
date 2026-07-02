import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:geolocator/geolocator.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/client.dart';
import '../../core/models/collecte_locale.dart';
import '../../core/services/client_service.dart';
import '../../core/services/sync_service.dart';
import '../../core/services/agent_service.dart';

String _generateUuid() {
  final r = Random.secure();
  String seg(int len) =>
      List.generate(len, (_) => r.nextInt(16).toRadixString(16)).join();
  final v = (8 + r.nextInt(4)).toRadixString(16);
  return '${seg(8)}-${seg(4)}-4${seg(3)}-$v${seg(3)}-${seg(12)}';
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

  Future<Position?> _determinePosition() async {
    try {
      final permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) return null;
      return await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
        timeLimit: const Duration(seconds: 4),
      );
    } catch (e) {
      debugPrint('Geolocator: $e');
      return null;
    }
  }

  Future<void> _onSubmit() async {
    final l10n = AppL10n.of(context);
    if (_selectedClient == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.nouvelleCollecteNoClient)));
      return;
    }
    final amount = double.tryParse(_amountController.text.replaceAll(RegExp(r'\s'), '')) ?? 0;
    if (amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.nouvelleCollecteInvalidAmount)));
      return;
    }

    setState(() => _submitting = true);
    try {
      double? lat;
      double? lon;
      Position? pos;
      try {
        pos = await _determinePosition();
        if (pos != null) {
          lat = pos.latitude;
          lon = pos.longitude;
        }
      } catch (e) {
        debugPrint('Failed to get position: $e');
      }

      final now = DateTime.now();
      final collecte = CollecteLocale(
        uuidMobile: _generateUuid(),
        clientIdExterne: _selectedClient!.idClient.toString(),
        nomClient: _selectedClient!.fullName,
        montantCollecte: amount,
        dateCollecte: DateFormat('yyyy-MM-dd').format(now),
        canalPaiement: _canal,
        latitude: lat,
        longitude: lon,
        createdAt: now,
      );

      await context.read<SyncService>().ajouterCollecteLocale(collecte);

      if (pos != null && mounted) {
        try {
          await context.read<AgentService>().updatePosition(
            latitude: pos.latitude,
            longitude: pos.longitude,
            precisionMetres: pos.accuracy,
            altitudeMetres: pos.altitude,
            vitesseKmh: pos.speed * 3.6,
            capDegres: pos.heading,
            source: 'COLLECTE',
            collecteUuid: collecte.uuidMobile,
          );
        } catch (e) {
          debugPrint('Failed to update agent position on server: $e');
        }
      }

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
    final l10n = AppL10n.of(context);
    return Scaffold(
      backgroundColor: context.bg,
      body: Column(
        children: [
          _buildTopBar(context, l10n),
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
                  _buildClientCard(l10n),
                  const SizedBox(height: 20),
                ],
                _buildAmountInput(l10n),
                const SizedBox(height: 20),
                _buildCanalSelector(l10n),
                const SizedBox(height: 32),
                _buildSubmitButton(l10n),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, AppL10n l10n) {
    return Container(
      color: AppColors.navyDark,
      padding: EdgeInsets.only(top: MediaQuery.of(context).padding.top + 8, bottom: 12, left: 4, right: 16),
      child: Row(
        children: [
          IconButton(icon: const Icon(Icons.arrow_back_rounded, color: Colors.white), onPressed: () => Navigator.of(context).pop()),
          Expanded(
            child: Text(l10n.nouvelleCollecteTitle,
              style: const TextStyle(fontFamily: 'Inter', fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchField() {
    final l10n = AppL10n.of(context);
    return Container(
      decoration: BoxDecoration(color: context.surface, borderRadius: BorderRadius.circular(12), border: Border.all(color: context.border)),
      child: TextField(
        controller: _searchController,
        style: TextStyle(fontFamily: 'Inter', fontSize: 14, color: context.text),
        decoration: InputDecoration(
          hintText: l10n.nouvelleCollecteSearchHint,
          hintStyle: TextStyle(fontFamily: 'Inter', fontSize: 14, color: context.textSec),
          prefixIcon: Icon(Icons.search_rounded, color: context.textSec, size: 20),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(vertical: 14),
        ),
      ),
    );
  }

  Widget _buildSearchResults() {
    return Container(
      margin: const EdgeInsets.only(top: 4),
      decoration: BoxDecoration(color: context.surface, borderRadius: BorderRadius.circular(12), border: Border.all(color: context.border)),
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _searchResults.length.clamp(0, 5),
        separatorBuilder: (_, __) => Divider(height: 1, color: context.border),
        itemBuilder: (_, i) {
          final c = _searchResults[i];
          return ListTile(
            dense: true,
            leading: CircleAvatar(
              backgroundColor: AppColors.teal.withOpacity(0.15),
              child: Text(c.initials, style: const TextStyle(color: AppColors.teal, fontWeight: FontWeight.bold, fontSize: 13)),
            ),
            title: Text(c.fullName, style: TextStyle(color: context.text, fontFamily: 'Inter', fontSize: 13)),
            subtitle: c.telephone != null
              ? Text(c.telephone!, style: TextStyle(color: context.textSec, fontSize: 11)) : null,
            onTap: () => _selectClient(c),
          );
        },
      ),
    );
  }

  Widget _buildClientCard(AppL10n l10n) {
    final c = _selectedClient!;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: context.surface, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.teal.withOpacity(0.5))),
      child: Row(
        children: [
          CircleAvatar(backgroundColor: AppColors.teal.withOpacity(0.15),
            child: Text(c.initials, style: const TextStyle(color: AppColors.teal, fontWeight: FontWeight.bold))),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(c.fullName, style: TextStyle(fontFamily: 'Inter', fontSize: 15, fontWeight: FontWeight.w700, color: context.text)),
                if (c.telephone != null)
                  Text(c.telephone!, style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec)),
              ],
            ),
          ),
          TextButton(
            onPressed: () => setState(() { _selectedClient = null; }),
            style: TextButton.styleFrom(foregroundColor: AppColors.teal),
            child: Text(l10n.nouvelleCollecteChangeClient, style: const TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }

  Widget _buildAmountInput(AppL10n l10n) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 20),
      decoration: context.cardBoxR(16),
      child: Column(
        children: [
          Text(l10n.nouvelleCollecteAmountLabel, style: TextStyle(fontFamily: 'Inter', fontSize: 12, fontWeight: FontWeight.w500, color: context.textSec)),
          const SizedBox(height: 8),
          TextField(
            controller: _amountController,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            textAlign: TextAlign.center,
            style: TextStyle(fontFamily: 'Inter', fontSize: 52, fontWeight: FontWeight.w800, color: context.text),
            decoration: const InputDecoration(border: InputBorder.none, isDense: true, contentPadding: EdgeInsets.zero),
          ),
          const SizedBox(height: 4),
          Text('FCFA', style: TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w500, color: context.textSec)),
        ],
      ),
    );
  }

  Widget _buildCanalSelector(AppL10n l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.nouvelleCollecteCanalLabel, style: TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w600, color: context.text)),
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
                  color: sel ? AppColors.teal.withOpacity(0.14) : context.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: sel ? AppColors.teal : context.border, width: sel ? 1.5 : 1),
                ),
                child: Text(canal, style: TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: sel ? FontWeight.w700 : FontWeight.w500, color: sel ? AppColors.teal : context.textSec)),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildSubmitButton(AppL10n l10n) {
    return SizedBox(
      width: double.infinity, height: 52,
      child: ElevatedButton(
        onPressed: _submitting ? null : _onSubmit,
        style: ElevatedButton.styleFrom(backgroundColor: AppColors.teal, foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)), elevation: 0),
        child: _submitting
          ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
          : Text(l10n.nouvelleCollecteSubmit, style: const TextStyle(fontFamily: 'Inter', fontSize: 16, fontWeight: FontWeight.w700)),
      ),
    );
  }
}
