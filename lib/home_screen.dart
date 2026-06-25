import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class HomeScreen extends StatefulWidget {
  final SharedPreferences prefs;
  const HomeScreen({super.key, required this.prefs});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const _ch = MethodChannel('com.example.meclicker/settings');

  // Controllers
  final _minPrice  = TextEditingController();
  final _maxPrice  = TextEditingController();
  final _minPickup = TextEditingController();
  final _maxDrop   = TextEditingController();

  // State
  bool _master     = false;
  bool _a11yOk     = false;
  bool _overlayOk  = false;
  int  _accepts    = 0;
  int  _latencyMs  = 0;
  Timer? _ticker;

  static const _yellow = Color(0xFFFFD600);
  static const _green  = Color(0xFF00E676);
  static const _card   = Color(0xFF1A1A1A);
  static const _border = Color(0xFF2A2A2A);
  static const _bg     = Color(0xFF0D0D0D);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadAll();
    _ticker = Timer.periodic(const Duration(seconds: 2), (_) => _refreshAll());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ticker?.cancel();
    for (final c in [_minPrice, _maxPrice, _minPickup, _maxDrop]) c.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) _refreshAll();
  }

  void _loadAll() {
    final p = widget.prefs;
    setState(() {
      _master = p.getBool('master_switch') ?? false;
      _minPrice.text  = _fmt(p.getInt('min_price'));
      _maxPrice.text  = _fmt(p.getInt('max_price'));
      _minPickup.text = _fmtF(p.getDouble('min_pickup'));
      _maxDrop.text   = _fmtF(p.getDouble('max_drop'));
    });
    _refreshAll();
  }

  String _fmt(int? v)    => (v == null || v == 0)   ? '' : v.toString();
  String _fmtF(double? v)=> (v == null || v == 0.0) ? '' : v.toString();

  void _save() {
    final p = widget.prefs;
    p.setInt('min_price',      int.tryParse(_minPrice.text.trim())     ?? 0);
    p.setInt('max_price',      int.tryParse(_maxPrice.text.trim())     ?? 0);
    p.setDouble('min_pickup',  double.tryParse(_minPickup.text.trim()) ?? 0.0);
    p.setDouble('max_drop',    double.tryParse(_maxDrop.text.trim())   ?? 0.0);
    p.setBool('master_switch',  _master);
  }

  Future<void> _refreshAll() async {
    await _checkA11y();
    await _checkOverlay();
    await _refreshStats();
  }

  Future<void> _refreshStats() async {
    try {
      final r = await _ch.invokeMapMethod<String, dynamic>('getStats');
      if (mounted && r != null) setState(() {
        _accepts   = (r['acceptCount']   as int?) ?? 0;
        _latencyMs = ((r['lastLatencyMs'] as int?) ?? 0).toInt();
      });
    } catch (_) {}
  }

  Future<void> _checkA11y() async {
    try {
      final ok = await _ch.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
      if (mounted) setState(() => _a11yOk = ok);
    } catch (_) {}
  }

  Future<void> _checkOverlay() async {
    try {
      final ok = await _ch.invokeMethod<bool>('isOverlayPermissionGranted') ?? false;
      if (mounted) setState(() => _overlayOk = ok);
    } catch (_) {}
  }

  Future<void> _toggle(bool val) async {
    if (val && !_a11yOk) {
      _snack('Enable Dr.Clicker in Accessibility Settings first');
      await _ch.invokeMethod('openAccessibilitySettings');
      return;
    }
    if (val && !_overlayOk) {
      _snack('Grant overlay permission for best results');
      await _ch.invokeMethod('requestOverlayPermission');
    }
    setState(() => _master = val);
    _save();
  }

  Future<void> _reset() async {
    await _ch.invokeMethod('resetStats');
    setState(() { _accepts = 0; _latencyMs = 0; });
  }

  void _snack(String m) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(m), duration: const Duration(seconds: 3)));

  @override
  Widget build(BuildContext context) {
    final active = _master && _a11yOk;
    return Scaffold(
      backgroundColor: _bg,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _header(active),
              const SizedBox(height: 20),
              // Permission banners
              if (!_a11yOk) ...[_a11yBanner(), const SizedBox(height: 12)],
              if (!_overlayOk) ...[_overlayBanner(), const SizedBox(height: 12)],
              _engineCard(),
              const SizedBox(height: 14),
              _filterCard(
                title: 'PRICE FILTER',
                subtitle: 'Rupees (₹)',
                icon: Icons.currency_rupee,
                left:  _field(_minPrice,  'Min Price',  '0'),
                right: _field(_maxPrice,  'Max Price',  '99999'),
              ),
              const SizedBox(height: 14),
              _filterCard(
                title: 'DISTANCE FILTER',
                subtitle: 'Kilometres (km)',
                icon: Icons.map_outlined,
                left:  _field(_minPickup, 'Min Pickup', '0.0'),
                right: _field(_maxDrop,   'Max Drop',   '999'),
              ),
              const SizedBox(height: 14),
              _statsRow(active),
              const SizedBox(height: 20),
              _saveBtn(),
              const SizedBox(height: 10),
              _resetBtn(),
              const SizedBox(height: 28),
              _footer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _header(bool active) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Container(
          width: 52, height: 52,
          decoration: BoxDecoration(
            color: _yellow,
            borderRadius: BorderRadius.circular(14),
            boxShadow: [BoxShadow(color: _yellow.withOpacity(0.35), blurRadius: 16, offset: const Offset(0, 4))],
          ),
          child: const Icon(Icons.bolt, color: Colors.black, size: 32),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Dr.Clicker',
                  style: TextStyle(fontSize: 26, fontWeight: FontWeight.w900,
                      color: Colors.white, letterSpacing: 0.3)),
              Text('Auto-Accept with filters · Rapido & Ola',
                  style: TextStyle(fontSize: 12, color: Colors.grey[500])),
            ],
          ),
        ),
        AnimatedContainer(
          duration: const Duration(milliseconds: 400),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(
            color: active ? _green.withOpacity(0.12) : Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: active ? _green.withOpacity(0.5) : Colors.grey.withOpacity(0.3)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 7, height: 7,
                  decoration: BoxDecoration(shape: BoxShape.circle,
                      color: active ? _green : Colors.grey)),
              const SizedBox(width: 6),
              Text(active ? 'ON' : 'OFF',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold,
                      color: active ? _green : Colors.grey)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _a11yBanner() {
    return GestureDetector(
      onTap: () => _ch.invokeMethod('openAccessibilitySettings'),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF2A1800),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: _yellow.withOpacity(0.45)),
        ),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded, color: _yellow, size: 20),
            const SizedBox(width: 10),
            const Expanded(
              child: Text('Tap here → Enable Dr.Clicker in\nAccessibility Settings',
                  style: TextStyle(color: _yellow, fontSize: 13, height: 1.4)),
            ),
            const Icon(Icons.arrow_forward_ios, color: _yellow, size: 14),
          ],
        ),
      ),
    );
  }

  Widget _overlayBanner() {
    return GestureDetector(
      onTap: () => _ch.invokeMethod('requestOverlayPermission'),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF001A2A),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFF00B0FF).withOpacity(0.45)),
        ),
        child: Row(
          children: [
            const Icon(Icons.layers_outlined, color: Color(0xFF00B0FF), size: 20),
            const SizedBox(width: 10),
            const Expanded(
              child: Text('Tap here → Grant Overlay\npermission for best results',
                  style: TextStyle(color: Color(0xFF00B0FF), fontSize: 13, height: 1.4)),
            ),
            const Icon(Icons.arrow_forward_ios, color: Color(0xFF00B0FF), size: 14),
          ],
        ),
      ),
    );
  }

  Widget _engineCard() {
    return _box(
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Engine Activation',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
                const SizedBox(height: 4),
                Text('Auto-tap Accept with price & distance filters',
                    style: TextStyle(fontSize: 12, color: Colors.grey[500])),
              ],
            ),
          ),
          Transform.scale(
            scale: 0.9,
            child: Switch(
              value: _master,
              onChanged: _toggle,
              activeColor: Colors.black,
              activeTrackColor: _yellow,
              inactiveThumbColor: Colors.grey[600],
              inactiveTrackColor: Colors.grey[800],
            ),
          ),
        ],
      ),
    );
  }

  Widget _filterCard({
    required String title,
    required String subtitle,
    required IconData icon,
    required Widget left,
    required Widget right,
  }) {
    return _box(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: _yellow, size: 16),
              const SizedBox(width: 7),
              Text(title,
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800,
                      color: _yellow, letterSpacing: 1.1)),
              const SizedBox(width: 6),
              Text(subtitle,
                  style: TextStyle(fontSize: 11, color: Colors.grey[600])),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(child: left),
              const SizedBox(width: 12),
              Expanded(child: right),
            ],
          ),
        ],
      ),
    );
  }

  Widget _statsRow(bool active) {
    return Row(
      children: [
        Expanded(
          child: _statBox(
            value: _accepts.toString(),
            label: 'Rides Accepted',
            icon: Icons.check_circle_outline_rounded,
            color: _accepts > 0 ? _yellow : Colors.grey,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _statBox(
            value: _latencyMs > 0 ? '${_latencyMs}ms' : '--',
            label: 'Last Latency',
            icon: Icons.timer_outlined,
            color: _latencyMs > 0 ? _green : Colors.grey,
          ),
        ),
      ],
    );
  }

  Widget _statBox({
    required String value,
    required String label,
    required IconData icon,
    required Color color,
  }) {
    return _box(
      child: Column(
        children: [
          Icon(icon, color: color, size: 22),
          const SizedBox(height: 10),
          Text(value,
              style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900, color: color)),
          const SizedBox(height: 4),
          Text(label,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: Colors.grey[600])),
        ],
      ),
    );
  }

  Widget _saveBtn() {
    return SizedBox(
      width: double.infinity,
      height: 54,
      child: ElevatedButton.icon(
        icon: const Icon(Icons.save_alt_rounded, size: 20),
        label: const Text('Save Settings',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        style: ElevatedButton.styleFrom(
          backgroundColor: _yellow,
          foregroundColor: Colors.black,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          elevation: 4,
          shadowColor: _yellow.withOpacity(0.4),
        ),
        onPressed: () { _save(); _snack('Settings saved ✓'); },
      ),
    );
  }

  Widget _resetBtn() {
    return SizedBox(
      width: double.infinity,
      height: 46,
      child: OutlinedButton.icon(
        icon: const Icon(Icons.refresh_rounded, size: 18),
        label: const Text('Reset Counter', style: TextStyle(fontSize: 14)),
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.grey[500],
          side: BorderSide(color: Colors.grey[800]!),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
        onPressed: _reset,
      ),
    );
  }

  Widget _footer() {
    return Center(
      child: Text(
        '⚡ Dr.Clicker v24.1 • 100% Free • No Login • No Ads • Fully Offline',
        style: TextStyle(fontSize: 11, color: Colors.grey[700]),
      ),
    );
  }

  Widget _box({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _border),
      ),
      child: child,
    );
  }

  Widget _field(TextEditingController ctrl, String label, String hint) {
    return TextField(
      controller: ctrl,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[\d.]'))],
      style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w500),
      cursorColor: _yellow,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        labelStyle: TextStyle(color: Colors.grey[600], fontSize: 13),
        hintStyle: TextStyle(color: Colors.grey[800], fontSize: 13),
        filled: true,
        fillColor: const Color(0xFF111111),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: Color(0xFF2E2E2E)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: _yellow, width: 1.5),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      ),
    );
  }
}
