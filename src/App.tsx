import React from 'react';
import {SafeAreaView, StatusBar, StyleSheet, Text, View} from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.content}>
        <Text style={styles.eyebrow}>CONNECT BEYOND DISTANCE</Text>
        <Text style={styles.title}>LINKO</Text>
        <Text style={styles.subtitle}>
          Authorized network sharing between Receiver and Provider devices.
        </Text>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Project foundation</Text>
          <Text style={styles.cardText}>
            React Native UI + Kotlin Android networking layer.
          </Text>
          <Text style={styles.cardText}>
            VPN, tunnel, signaling, direct path and relay fallback will be
            implemented in their approved phases.
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#050912'},
  content: {flex: 1, padding: 28, justifyContent: 'center'},
  eyebrow: {color: '#7fe3ff', fontSize: 11, letterSpacing: 2.5, marginBottom: 10},
  title: {color: '#eaf2ff', fontSize: 44, fontWeight: '700'},
  subtitle: {color: '#9fb3d1', fontSize: 16, lineHeight: 24, marginTop: 10},
  card: {marginTop: 28, padding: 20, borderRadius: 16, backgroundColor: '#0e1830', borderWidth: 1, borderColor: '#1c2c4d'},
  cardTitle: {color: '#5eead4', fontSize: 16, fontWeight: '700'},
  cardText: {color: '#9fb3d1', fontSize: 13, lineHeight: 20, marginTop: 10},
});
