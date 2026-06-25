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

  final _minPrice  = TextEditingController();
  final _maxPrice  = TextEditingController();
  final _minPickup = TextEditingController();
  final _maxDrop   = TextEditingController();

  bool _master      = false;
  bool _a11yOk      = false;
  bool _overlayOk   = false;
  bool _batteryOk   = false;
  bool _floatOn     = false;
  int  _accepts     = 0;
  int  _latencyMs   = 0;
  Timer? _ticker;

  static const _yellow = Color(0xFFFFD600);
  static const _green  = Color(0xFF00E676);
  static const _red    = Color(0xFFEF5350);
  static const _blue   = Color(0xFF00B0FF);
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
    p.setInt('min_price',    int.tryParse(_minPrice.text.trim())     ?? 0);
    p.setInt('max_price',    int.tryParse(_maxPrice.text.trim())     ?? 0);
    p.setDouble('min_pickup', double.tryParse(_minPickup.text.trim()) ?? 0.0);
    p.setDouble('max_drop',   double.tryParse(_maxDrop.text.trim())  ?? 0.0);
    p.setBool('master_switch', _master);
  }

  Future<void> _refreshAll() async {
    await Future.wait([
      _checkA11y(),
      _checkOverlay(),
      _checkBattery(),
      _checkFloat(),
      _refreshStats(),
    ]);
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

  Future<void> _checkBattery() async {
    try {
      final ok = await _ch.invokeMethod<bool>('isBatteryOptimizationIgnored') ?? false;
      if (mounted) setState(() => _batteryOk = ok);
    } catch (_) {}
  }

  Future<void> _checkFloat() async {
    try {
      final ok = await _ch.invokeMethod<bool>('isFloatingPanelRunning') ?? false;
      if (mounted) setState(() => _floatOn = ok);
    } catch (_) {}
  }

  Future<void> _toggle(bool val) async {
    if (val && !_a11yOk) {
      _snack('Pehle Accessibility mein Dr.Clicker enable karo!');
      await _ch.invokeMethod('openAccessibilitySettings');
      return;
    }
    if (val && !_overlayOk) {
      _snack('Overlay permission do — floating panel ke liye zaroori hai');
      await _ch.invokeMethod('requestOverlayPermission');
      return;
    }
    setState(() => _master = val);
    _save();
    _snack(val ? '⚡ Engine ON — Screen monitor ho raha hai!' : '🔴 Engine OFF');
  }

  Future<void> _toggleFloat(bool val) async {
    if (val && !_overlayOk) {
      _snack('Overlay permission zaroori hai — Tap karo "Overlay Grant"');
      await _ch.invokeMethod('requestOverlayPermission');
      return;
    }
    if (val) {
      final ok = await _ch.invokeMethod<bool>('startFloatingPanel') ?? false;
      setState(() => _floatOn = ok);
      if (!ok) _snack('Overlay permission nahi mili');
      else _snack('🟢 Floating panel chalu hua!');
    } else {
      await _ch.invokeMethod('stopFloatingPanel');
      setState(() => _floatOn = false);
      _snack('Floating panel band hua');
    }
  }

  Future<void> _reset() async {
    await _ch.invokeMethod('resetStats');
    setState(() { _accepts = 0; _latencyMs = 0; });
  }

  void _snack(String m) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(
        content: Text(m),
        duration: const Duration(seconds: 3),
        backgroundColor: const Color(0xFF1A1A1A),
      ));

  @override
  Widget build(BuildContext context) {
    final active = _master && _a11yOk;
    return Scaffold(
      backgroundColor: _bg,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _header(active),
              const SizedBox(height: 16),

              // Permission Steps
              _permSection(active),
              const SizedBox(height: 14),

              // Engine card
              _engineCard(),
              const SizedBox(height: 12),

              // Floating panel card
              _floatCard(),
              const SizedBox(height: 14),

              // Filters
              _filterCard(
                title: 'PRICE FILTER',
                subtitle: 'Rupees (₹) — 0 = koi limit nahi',
                icon: Icons.currency_rupee,
                left:  _field(_minPrice,  'Min ₹',   '0'),
                right: _field(_maxPrice,  'Max ₹',   '9999'),
              ),
              const SizedBox(height: 12),
              _filterCard(
                title: 'DISTANCE FILTER',
                subtitle: 'Kilometres — 0 = koi limit nahi',
                icon: Icons.map_outlined,
                left:  _field(_minPickup, 'Min KM', '0'),
                right: _field(_maxDrop,   'Max KM', '999'),
              ),
              const SizedBox(height: 14),

              // Stats
              _statsRow(active),
              const SizedBox(height: 18),

              // Save
              _saveBtn(),
              const SizedBox(height: 10),
              _resetBtn(),
              const SizedBox(height: 24),
              _footer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _header(bool active) {
    return Row(
      children: [
        Container(
          width: 50, height: 50,
          decoration: BoxDecoration(
            color: _yellow,
            borderRadius: BorderRadius.circular(14),
            boxShadow: [BoxShadow(color: _yellow.withOpacity(0.35), blurRadius: 16, offset: const Offset(0, 4))],
          ),
          child: const Icon(Icons.bolt, color: Colors.black, size: 30),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Dr.Clicker',
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Colors.white)),
              Text('Auto-Accept • Rapido & Ola • 100% Free',
                  style: TextStyle(fontSize: 11, color: Colors.grey[500])),
            ],
          ),
        ),
        AnimatedContainer(
          duration: const Duration(milliseconds: 400),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
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
              const SizedBox(width: 5),
              Text(active ? 'ON' : 'OFF',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold,
                      color: active ? _green : Colors.grey)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _permSection(bool active) {
    return _box(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.security, color: _yellow, size: 15),
            const SizedBox(width: 6),
            const Text('PERMISSIONS — YEH TEEN STEP KARO',
                style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800,
                    color: _yellow, letterSpacing: 0.8)),
          ]),
          const SizedBox(height: 12),
          _permRow(
            step: '1',
            label: 'Accessibility Enable Karo',
            ok: _a11yOk,
            onTap: () => _ch.invokeMethod('openAccessibilitySettings'),
            color: _yellow,
          ),
          const SizedBox(height: 8),
          _permRow(
            step: '2',
            label: 'Overlay Permission Do',
            ok: _overlayOk,
            onTap: () => _ch.invokeMethod('requestOverlayPermission'),
            color: _blue,
          ),
          const SizedBox(height: 8),
          _permRow(
            step: '3',
            label: 'Battery Optimization Hatao',
            ok: _batteryOk,
            onTap: () => _ch.invokeMethod('requestIgnoreBatteryOptimization'),
            color: _green,
          ),
        ],
      ),
    );
  }

  Widget _permRow({
    required String step,
    required String label,
    required bool ok,
    required VoidCallback onTap,
    required Color color,
  }) {
    return GestureDetector(
      onTap: ok ? null : onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: ok ? color.withOpacity(0.08) : const Color(0xFF111111),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: ok ? color.withOpacity(0.4) : const Color(0xFF333333),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 22, height: 22,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: ok ? color.withOpacity(0.2) : const Color(0xFF222222),
              ),
              child: Center(
                child: ok
                    ? Icon(Icons.check, color: color, size: 13)
                    : Text(step, style: TextStyle(
                        color: color, fontSize: 11, fontWeight: FontWeight.bold)),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(label,
                  style: TextStyle(
                      color: ok ? color : Colors.white,
                      fontSize: 13,
                      fontWeight: ok ? FontWeight.w500 : FontWeight.w600)),
            ),
            if (!ok)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: color.withOpacity(0.4)),
                ),
                child: Text('TAP', style: TextStyle(color: color, fontSize: 10,
                    fontWeight: FontWeight.bold)),
              ),
          ],
        ),
      ),
    );
  }

  Widget _engineCard() {
    return _box(
      child: Row(
        children: [
          Container(
            width: 40, height: 40,
            decoration: BoxDecoration(
              color: _master ? _yellow.withOpacity(0.15) : const Color(0xFF111111),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: _master ? _yellow.withOpacity(0.4) : const Color(0xFF333333)),
            ),
            child: Icon(Icons.bolt, color: _master ? _yellow : Colors.grey, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Engine Activation',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
                Text(_master && _a11yOk
                    ? '⚡ Screen monitor ho raha hai — auto-click chalega'
                    : 'Ride aayegi toh auto-accept karega',
                    style: TextStyle(fontSize: 11, color: Colors.grey[500])),
              ],
            ),
          ),
          Transform.scale(
            scale: 0.85,
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

  Widget _floatCard() {
    return _box(
      child: Row(
        children: [
          Container(
            width: 40, height: 40,
            decoration: BoxDecoration(
              color: _floatOn ? _blue.withOpacity(0.15) : const Color(0xFF111111),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: _floatOn ? _blue.withOpacity(0.4) : const Color(0xFF333333)),
            ),
            child: Icon(Icons.picture_in_picture_alt_rounded,
                color: _floatOn ? _blue : Colors.grey, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Floating Panel',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
                Text(_floatOn
                    ? '🟢 Screen pe dikhta hai — drag kar sako'
                    : 'Rapido/Ola ke upar status panel',
                    style: TextStyle(fontSize: 11, color: Colors.grey[500])),
              ],
            ),
          ),
          Transform.scale(
            scale: 0.85,
            child: Switch(
              value: _floatOn,
              onChanged: _toggleFloat,
              activeColor: Colors.black,
              activeTrackColor: _blue,
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
          Row(children: [
            Icon(icon, color: _yellow, size: 14),
            const SizedBox(width: 6),
            Text(title,
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800,
                    color: _yellow, letterSpacing: 1.0)),
          ]),
          const SizedBox(height: 4),
          Text(subtitle, style: TextStyle(fontSize: 10, color: Colors.grey[700])),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: left),
            const SizedBox(width: 10),
            Expanded(child: right),
          ]),
        ],
      ),
    );
  }

  Widget _statsRow(bool active) {
    return Row(
      children: [
        Expanded(child: _statBox(
          value: _accepts.toString(),
          label: 'Rides Accepted',
          icon: Icons.check_circle_outline_rounded,
          color: _accepts > 0 ? _yellow : Colors.grey,
        )),
        const SizedBox(width: 12),
        Expanded(child: _statBox(
          value: _latencyMs > 0 ? '${_latencyMs}ms' : '--',
          label: 'Last Click Speed',
          icon: Icons.timer_outlined,
          color: _latencyMs > 0 ? _green : Colors.grey,
        )),
      ],
    );
  }

  Widget _statBox({required String value, required String label,
      required IconData icon, required Color color}) {
    return _box(
      child: Column(children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 8),
        Text(value, style: TextStyle(fontSize: 26, fontWeight: FontWeight.w900, color: color)),
        const SizedBox(height: 4),
        Text(label, textAlign: TextAlign.center,
            style: TextStyle(fontSize: 11, color: Colors.grey[600])),
      ]),
    );
  }

  Widget _saveBtn() {
    return SizedBox(
      width: double.infinity, height: 52,
      child: ElevatedButton.icon(
        icon: const Icon(Icons.save_alt_rounded, size: 19),
        label: const Text('Save Settings',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
        style: ElevatedButton.styleFrom(
          backgroundColor: _yellow, foregroundColor: Colors.black,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)),
          elevation: 4, shadowColor: _yellow.withOpacity(0.4),
        ),
        onPressed: () { _save(); _snack('✅ Settings save ho gayi!'); },
      ),
    );
  }

  Widget _resetBtn() {
    return SizedBox(
      width: double.infinity, height: 44,
      child: OutlinedButton.icon(
        icon: const Icon(Icons.refresh_rounded, size: 17),
        label: const Text('Reset Counter', style: TextStyle(fontSize: 13)),
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.grey[500],
          side: BorderSide(color: Colors.grey[800]!),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(11)),
        ),
        onPressed: _reset,
      ),
    );
  }

  Widget _footer() {
    return Center(
      child: Text(
        '⚡ Dr.Clicker v24.1 • 100% Free • Offline • No Login',
        style: TextStyle(fontSize: 10, color: Colors.grey[800]),
      ),
    );
  }

  Widget _box({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _card,
        borderRadius: BorderRadius.circular(14),
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
      style: const TextStyle(color: Colors.white, fontSize: 15),
      cursorColor: _yellow,
      decoration: InputDecoration(
        labelText: label, hintText: hint,
        labelStyle: TextStyle(color: Colors.grey[600], fontSize: 12),
        hintStyle: TextStyle(color: Colors.grey[800], fontSize: 12),
        filled: true, fillColor: const Color(0xFF111111),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: Color(0xFF2E2E2E)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: _yellow, width: 1.5),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      ),
    );
  }
}
