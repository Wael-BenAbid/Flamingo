import * as React from 'react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Chrome, Loader2, LogIn, Lock, Mail, Waves, Eye, EyeOff, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { USER_ROLES, type StaffRole } from '../../shared/constants';

const ROLE_HOME_ROUTE: Record<StaffRole, string> = {
  admin:       '/',
  responsable: '/',
  cuisinier:   '/kitchen',
  barman:      '/kitchen',
  serveur:     '/place-order',
  none:        '/login',
};

function getLoginErrorMessage(error: unknown): string {
  const code = typeof error === 'object' && error !== null && 'code' in error
    ? String((error as { code?: string }).code)
    : '';

  switch (code) {
    case 'auth/invalid-email':
      return 'Adresse e-mail invalide.';
    case 'auth/user-not-found':
    case 'auth/wrong-password':
    case 'auth/invalid-credential':
      return 'E-mail ou mot de passe incorrect.';
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return 'La connexion Google a été annulée.';
    case 'auth/too-many-requests':
      return 'Trop de tentatives. Réessayez plus tard.';
    default:
      return 'Impossible de vous connecter. Vérifiez vos identifiants.';
  }
}

export default function Login() {
  const { user, role, loginWithEmailPassword, loginWithGoogle, loading, logout } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const redirectTarget = user && role !== USER_ROLES.NONE
    ? ROLE_HOME_ROUTE[role as StaffRole] || '/'
    : null;

  useEffect(() => {
    if (!loading && redirectTarget) {
      navigate(redirectTarget, { replace: true });
    }
  }, [loading, navigate, redirectTarget]);

  useEffect(() => {
    const savedEmail = localStorage.getItem('remember_email');
    if (savedEmail) {
      setEmail(savedEmail);
      setRememberMe(true);
    }
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-6 text-slate-500">
          <div className="relative">
            <div className="w-14 h-14 rounded-2xl bg-flamingo/10 flex items-center justify-center">
              <Loader2 className="w-8 h-8 animate-spin text-flamingo" />
            </div>
            <div className="absolute inset-0 rounded-2xl border-2 border-flamingo/20 border-t-flamingo animate-spin" style={{ animationDuration: '3s' }} />
          </div>
          <p className="text-[11px] uppercase tracking-[0.35em] font-bold">Chargement</p>
        </div>
      </div>
    );
  }
  if (user && redirectTarget) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-6 text-slate-500">
          <div className="w-14 h-14 rounded-2xl bg-flamingo/10 flex items-center justify-center">
            <Loader2 className="w-8 h-8 animate-spin text-flamingo" />
          </div>
          <div className="text-center">
            <p className="text-[11px] uppercase tracking-[0.35em] font-bold mb-2">Ouverture de votre espace</p>
            <p className="text-sm text-slate-400">Redirection en cours...</p>
          </div>
        </div>
      </div>
    );
  }

  if (user) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <Card className="w-full max-w-[520px] border border-black/5 shadow-xl">
          <CardHeader className="space-y-3">
            <CardTitle className="text-2xl font-serif">Profil connecté, mais rôle manquant</CardTitle>
            <CardDescription>
              Votre compte est bien authentifié, mais l’application n’a trouvé aucun rôle exploitable dans le profil `workers`.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-sm text-slate-600">
            <p>
              Vérifiez que le compte possède bien un document Firestore dans `workers` avec un champ `role`
              ou `category` renseigné sur une valeur métier valide comme `cuisine`, `serveur` ou `responsable`.
            </p>
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-800">
              En attendant, vous pouvez vous déconnecter puis réessayer avec le bon profil.
            </div>
          </CardContent>
          <CardFooter className="justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={logout}
              className="border-slate-200"
            >
              Se déconnecter
            </Button>
          </CardFooter>
        </Card>
      </div>
    );
  }

  const handleEmailLogin = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    if (!email.trim() || !password) {
      setError('Veuillez saisir votre adresse e-mail et votre mot de passe.');
      return;
    }

    if (rememberMe) {
      localStorage.setItem('remember_email', email.trim());
    } else {
      localStorage.removeItem('remember_email');
    }

    setSubmitting(true);
    try {
      await loginWithEmailPassword(email, password);
    } catch (authError) {
      setError(getLoginErrorMessage(authError));
      setSubmitting(false);
    }
  };

  const handleGoogleLogin = async () => {
    setError(null);
    setSubmitting(true);

    try {
      await loginWithGoogle();
    } catch (authError) {
      setError(getLoginErrorMessage(authError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex relative overflow-hidden">
      {/* Left panel — image décorative */}
      <div className="hidden lg:flex flex-col justify-between w-[45%] bg-navy relative overflow-hidden p-12">
        <div className="absolute inset-0 bg-gradient-to-br from-navy via-navy/90 to-flamingo/20" />
        <img
          src="https://firebasestorage.googleapis.com/v0/b/flamingo-ea5e5.firebasestorage.app/o/flamingo.jpeg?alt=media&token=eb138c1e-a9e1-405a-9b47-de81c2588b88"
          alt="Flamingo"
          className="absolute inset-0 w-full h-full object-cover opacity-20 mix-blend-luminosity"
        />
        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-2">
            <img src="https://firebasestorage.googleapis.com/v0/b/flamingo-ea5e5.firebasestorage.app/o/flamingo.jpeg?alt=media&token=eb138c1e-a9e1-405a-9b47-de81c2588b88" alt="Logo" className="w-12 h-12 rounded-xl object-cover shadow-lg" />
            <span className="text-white text-2xl font-bold tracking-widest uppercase">Flamingo</span>
          </div>
          <p className="text-white/50 text-xs uppercase tracking-widest">Beach Club & Restaurant</p>
        </div>
        <div className="relative z-10">
          <p className="text-white/70 text-lg font-light leading-relaxed italic">
            "L'excellence au bord de la mer, chaque jour."
          </p>
          <div className="mt-6 flex gap-2">
            {[1,2,3].map(i => <div key={i} className={`h-1 rounded-full ${i===1 ? 'w-8 bg-flamingo' : 'w-4 bg-white/20'}`} />)}
          </div>
        </div>
      </div>

      {/* Right panel — formulaire */}
      <div className="flex-1 flex items-center justify-center bg-sand px-8 py-12 relative">
        <div className="absolute top-0 right-0 w-96 h-96 bg-flamingo/5 rounded-full blur-3xl -translate-y-1/2 translate-x-1/4" />
        <div className="absolute bottom-0 left-0 w-64 h-64 bg-navy/5 rounded-full blur-3xl translate-y-1/2 -translate-x-1/4" />

      <Card className="w-full max-w-[420px] relative z-10 shadow-xl border border-border/50 bg-white/80 backdrop-blur-sm">
        <CardHeader className="text-center space-y-4 pb-2 pt-8">
          <div className="mx-auto w-20 h-20 rounded-2xl overflow-hidden shadow-xl shadow-flamingo/20 transition-transform duration-300 hover:scale-105 border-2 border-flamingo/20">
            <img src="https://firebasestorage.googleapis.com/v0/b/flamingo-ea5e5.firebasestorage.app/o/flamingo.jpeg?alt=media&token=eb138c1e-a9e1-405a-9b47-de81c2588b88" alt="Flamingo Logo" className="w-full h-full object-cover" />
          </div>
          <div className="space-y-1">
            <CardTitle className="text-3xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-flamingo to-navy">
              Flamingo
            </CardTitle>
            <CardDescription className="text-slate-500 font-medium">
              Gestion Premium Beach Club
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-5 pt-2 pb-6">
          <p className="text-sm text-slate-500 text-center px-6">
            Connectez-vous avec votre e-mail/mot de passe administrateur ou avec Google pour accéder à la plateforme.
          </p>
          <form className="space-y-4" onSubmit={handleEmailLogin}>
            <div className="space-y-2">
              <Label htmlFor="email" className="text-slate-700 font-semibold">
                Adresse e-mail
              </Label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="exemple@flamingo.com"
                  className="h-12 rounded-xl border-slate-200 bg-white/90 pl-11 pr-4 text-slate-900 placeholder:text-slate-400 focus-visible:border-flamingo focus-visible:ring-flamingo/20"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  disabled={submitting}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password" className="text-slate-700 font-semibold">
                Mot de passe
              </Label>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  className="h-12 rounded-xl border-slate-200 bg-white/90 pl-11 pr-12 text-slate-900 placeholder:text-slate-400 focus-visible:border-flamingo focus-visible:ring-flamingo/20"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={submitting}
                />
                <button
                  type="button"
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between pt-1">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="remember"
                  checked={rememberMe}
                  onCheckedChange={(checked) => setRememberMe(!!checked)}
                  disabled={submitting}
                />
                <Label htmlFor="remember" className="text-sm text-slate-600 cursor-pointer">
                  Se souvenir de moi
                </Label>
              </div>
            </div>

            {error && (
              <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm">
                <AlertCircle className="h-4 w-4 text-red-600 mt-0.5 flex-shrink-0" />
                <p className="text-red-700">{error}</p>
              </div>
            )}

            <Button
              type="submit"
              className="w-full h-12 text-base font-semibold bg-primary hover:opacity-90 transition-all rounded-xl gap-3 shadow-lg shadow-flamingo/25"
              disabled={submitting}
            >
              {submitting ? <Loader2 className="w-5 h-5 animate-spin" /> : <LogIn className="w-5 h-5" />}
              Connexion
            </Button>
          </form>
        </CardContent>
<CardFooter className="flex-col items-stretch gap-4 bg-transparent border-t border-slate-200/70 pt-5">
          <div className="flex items-center gap-3 text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-400">
            <span className="h-px flex-1 bg-slate-200" />
            <span>ou continuer avec</span>
            <span className="h-px flex-1 bg-slate-200" />
          </div>
          <Button
            type="button"
            variant="outline"
            className="w-full h-12 text-base font-semibold rounded-xl gap-3 border-slate-200 bg-white/80 hover:bg-white hover:border-flamingo/30 transition-all"
            onClick={handleGoogleLogin}
            disabled={submitting}
          >
            {submitting ? <Loader2 className="w-5 h-5 animate-spin" /> : <Chrome className="w-5 h-5" />}
            Connexion avec Google
          </Button>
        </CardFooter>
       </Card>
       
        <div className="absolute bottom-8 text-slate-400 text-xs font-medium flex items-center gap-2">
          <span>&copy; 2026 Flamingo</span>
          <span className="text-slate-300">•</span>
          <span>créé par wael_ben_abid</span>
        </div>
      </div>
    </div>
  );
}
