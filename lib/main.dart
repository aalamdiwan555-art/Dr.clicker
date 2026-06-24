import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));
  final prefs = await SharedPreferences.getInstance();
  runApp(DrClickerApp(prefs: prefs));
}

class DrClickerApp extends StatelessWidget {
  final SharedPreferences prefs;
  const DrClickerApp({super.key, required this.prefs});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Dr.Clicker',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFFFD600),
          secondary: Color(0xFFFFD600),
          surface: Color(0xFF1A1A1A),
          onPrimary: Colors.black,
          onSurface: Colors.white,
        ),
        scaffoldBackgroundColor: const Color(0xFF0D0D0D),
        useMaterial3: true,
      ),
      home: HomeScreen(prefs: prefs),
    );
  }
}
