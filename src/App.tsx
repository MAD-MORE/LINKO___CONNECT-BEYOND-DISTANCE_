import React, {useState} from 'react';
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';

type Mode = 'receiver' | 'provider';

export default function App() {
  const [mode, setMode] = useState<Mode>('receiver');

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#050912" />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <View style={styles.brandRow}>
            <View style={styles.logo}><Text style={styles.logoText}>L</Text></View>
            <Text style={styles.brand}>LINKO</Text>
          </View>
          <Pressable style={styles.iconButton} accessibilityLabel="Settings">
            <Text style={styles.icon}>⚙</Text>
          </Pressable>
        </View>

        <View style={styles.statusRow}>
          <View style={styles.statusDot} />
          <Text style={styles.statusText}>READY TO CONNECT</Text>
          <Text style={styles.secure}>SECURE</Text>
        </View>

        <Text style={styles.title}>Connect beyond{ '\n' }distance.</Text>
        <Text style={styles.subtitle}>
          Share or receive an authorized internet connection through Linko.
        </Text>

        <View style={styles.modeSwitch}>
          <Pressable
            onPress={() => setMode('receiver')}
            style={[styles.modeTab, mode === 'receiver' && styles.modeTabActive]}>
            <Text style={[styles.modeText, mode === 'receiver' && styles.modeTextActive]}>RECEIVER</Text>
            <Text style={styles.modeHint}>Get connection</Text>
          </Pressable>
          <Pressable
            onPress={() => setMode('provider')}
            style={[styles.modeTab, mode === 'provider' && styles.modeTabActive]}>
            <Text style={[styles.modeText, mode === 'provider' && styles.modeTextActive]}>PROVIDER</Text>
            <Text style={styles.modeHint}>Share connection</Text>
          </Pressable>
        </View>

        {mode === 'receiver' ? (
          <View style={styles.heroCard}>
            <View style={styles.glowCircle}>
              <View style={styles.connectCircle}>
                <Text style={styles.connectIcon}>↗</Text>
                <Text style={styles.connectLabel}>CONNECT</Text>
              </View>
            </View>
            <Text style={styles.cardTitle}>Get internet help</Text>
            <Text style={styles.cardBody}>
              Choose an approved Provider and request a connection.
            </Text>
            <Pressable style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>FIND A PROVIDER</Text>
              <Text style={styles.arrow}>→</Text>
            </Pressable>
          </View>
        ) : (
          <View style={styles.heroCard}>
            <View style={styles.providerOrb}>
              <Text style={styles.providerIcon}>⌁</Text>
            </View>
            <Text style={styles.cardTitle}>Share your connection</Text>
            <Text style={styles.cardBody}>
              Accept an authorized request when you're ready to help someone.
            </Text>
            <Pressable style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>START SHARING</Text>
              <Text style={styles.arrow}>→</Text>
            </Pressable>
          </View>
        )}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>HOW LINKO CONNECTS</Text>
          <Text style={styles.sectionCode}>01 — 04</Text>
        </View>

        <View style={styles.stepsCard}>
          <Step number="01" title="Request" text="Receiver chooses a Provider." />
          <Step number="02" title="Authorize" text="Provider accepts the request." />
          <Step number="03" title="Connect" text="Linko coordinates the endpoints." />
          <Step number="04" title="Tunnel" text="Authorized traffic enters the Linko path." last />
        </View>

        <View style={styles.bottomRow}>
          <View>
            <Text style={styles.bottomLabel}>CONNECTION</Text>
            <Text style={styles.bottomValue}>Not connected</Text>
          </View>
          <View style={styles.directBadge}>
            <View style={styles.tinyDot} />
            <Text style={styles.directText}>DIRECT FIRST</Text>
          </View>
        </View>

        <Text style={styles.disclaimer}>
          Linko only uses a connection after the Provider explicitly authorizes the session.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function Step({number, title, text, last}: {number: string; title: string; text: string; last?: boolean}) {
  return (
    <View style={[styles.step, !last && styles.stepBorder]}>
      <View style={styles.stepNumber}><Text style={styles.stepNumberText}>{number}</Text></View>
      <View style={styles.stepCopy}>
        <Text style={styles.stepTitle}>{title}</Text>
        <Text style={styles.stepText}>{text}</Text>
      </View>
      <Text style={styles.stepArrow}>›</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#050912'},
  content: {paddingHorizontal: 20, paddingTop: 16, paddingBottom: 36},
  header: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  brandRow: {flexDirection: 'row', alignItems: 'center', gap: 10},
  logo: {width: 34, height: 34, borderRadius: 10, backgroundColor: '#102c42', borderWidth: 1, borderColor: '#4fd1ff', alignItems: 'center', justifyContent: 'center'},
  logoText: {color: '#bff6ff', fontSize: 19, fontWeight: '800'},
  brand: {color: '#eaf2ff', fontSize: 20, fontWeight: '800', letterSpacing: 2},
  iconButton: {width: 38, height: 38, borderRadius: 12, backgroundColor: '#0d1628', borderWidth: 1, borderColor: '#1c2c4d', alignItems: 'center', justifyContent: 'center'},
  icon: {color: '#9fb3d1', fontSize: 18},
  statusRow: {marginTop: 22, flexDirection: 'row', alignItems: 'center', gap: 7},
  statusDot: {width: 7, height: 7, borderRadius: 4, backgroundColor: '#5eead4'},
  statusText: {color: '#5eead4', fontSize: 10, fontWeight: '700', letterSpacing: 1.3},
  secure: {marginLeft: 'auto', color: '#6681a8', fontSize: 9, letterSpacing: 1.2},
  title: {marginTop: 20, color: '#eaf2ff', fontSize: 39, lineHeight: 43, fontWeight: '800', letterSpacing: -1.1},
  subtitle: {marginTop: 12, color: '#91a7c8', fontSize: 14, lineHeight: 21, maxWidth: 350},
  modeSwitch: {marginTop: 25, flexDirection: 'row', padding: 4, borderRadius: 14, backgroundColor: '#0b1425', borderWidth: 1, borderColor: '#182743'},
  modeTab: {flex: 1, paddingVertical: 12, paddingHorizontal: 10, borderRadius: 10},
  modeTabActive: {backgroundColor: '#122b3e', borderWidth: 1, borderColor: '#27556a'},
  modeText: {textAlign: 'center', color: '#7088aa', fontSize: 10, fontWeight: '800', letterSpacing: 1.2},
  modeTextActive: {color: '#bff6ff'},
  modeHint: {marginTop: 4, textAlign: 'center', color: '#526a8d', fontSize: 9},
  heroCard: {marginTop: 18, padding: 24, borderRadius: 24, backgroundColor: '#0c172a', borderWidth: 1, borderColor: '#1d3151', alignItems: 'center', overflow: 'hidden'},
  glowCircle: {width: 124, height: 124, borderRadius: 62, backgroundColor: '#0c2536', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#28526a'},
  connectCircle: {width: 88, height: 88, borderRadius: 44, backgroundColor: '#102b3c', borderWidth: 1.5, borderColor: '#4fd1ff', alignItems: 'center', justifyContent: 'center'},
  connectIcon: {color: '#7fe3ff', fontSize: 24, marginBottom: 2},
  connectLabel: {color: '#c9f7ff', fontSize: 9, fontWeight: '800', letterSpacing: 1.4},
  providerOrb: {width: 124, height: 124, borderRadius: 62, backgroundColor: '#102a2b', borderWidth: 1, borderColor: '#31756d', alignItems: 'center', justifyContent: 'center'},
  providerIcon: {color: '#5eead4', fontSize: 50, fontWeight: '300'},
  cardTitle: {marginTop: 18, color: '#eaf2ff', fontSize: 20, fontWeight: '700'},
  cardBody: {marginTop: 8, color: '#849abd', fontSize: 12.5, lineHeight: 19, textAlign: 'center', maxWidth: 290},
  primaryButton: {marginTop: 20, minWidth: 220, paddingVertical: 14, paddingHorizontal: 18, borderRadius: 13, backgroundColor: '#4fd1ff', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12},
  primaryButtonText: {color: '#04111c', fontSize: 11, fontWeight: '900', letterSpacing: 1},
  arrow: {color: '#04111c', fontSize: 17, fontWeight: '800'},
  sectionHeader: {marginTop: 28, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  sectionTitle: {color: '#a9bfdd', fontSize: 10, fontWeight: '800', letterSpacing: 1.5},
  sectionCode: {color: '#536b8e', fontSize: 9, fontWeight: '600'},
  stepsCard: {marginTop: 10, paddingHorizontal: 15, borderRadius: 18, backgroundColor: '#0a1323', borderWidth: 1, borderColor: '#182943'},
  step: {minHeight: 68, flexDirection: 'row', alignItems: 'center', paddingVertical: 12},
  stepBorder: {borderBottomWidth: 1, borderBottomColor: '#15243b'},
  stepNumber: {width: 34, height: 34, borderRadius: 10, backgroundColor: '#0f2035', borderWidth: 1, borderColor: '#213957', alignItems: 'center', justifyContent: 'center'},
  stepNumberText: {color: '#5eead4', fontSize: 9, fontWeight: '800'},
  stepCopy: {flex: 1, marginLeft: 12},
  stepTitle: {color: '#dce8fa', fontSize: 13, fontWeight: '700'},
  stepText: {marginTop: 3, color: '#687f9f', fontSize: 10.5},
  stepArrow: {color: '#3d5578', fontSize: 22, paddingLeft: 8},
  bottomRow: {marginTop: 18, padding: 16, borderRadius: 15, backgroundColor: '#0d1729', borderWidth: 1, borderColor: '#192a45', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  bottomLabel: {color: '#526b8e', fontSize: 8.5, fontWeight: '800', letterSpacing: 1.3},
  bottomValue: {marginTop: 4, color: '#b8c9e1', fontSize: 12},
  directBadge: {flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 7, paddingHorizontal: 9, borderRadius: 8, backgroundColor: '#0e211f'},
  tinyDot: {width: 5, height: 5, borderRadius: 3, backgroundColor: '#5eead4'},
  directText: {color: '#5eead4', fontSize: 8, fontWeight: '800', letterSpacing: 1},
  disclaimer: {marginTop: 14, color: '#465c7c', fontSize: 9.5, lineHeight: 15, textAlign: 'center'},
});
