import * as React from 'react';
import { useState } from 'react';

interface UserAvatarProps {
  photoURL: string | null;
  displayName: string | null;
  email: string | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizeClasses = {
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-sm',
  lg: 'w-12 h-12 text-base'
};

/**
 * UserAvatar Component
 * Displays user profile picture with intelligent fallback
 * - Caches image to prevent repeated requests
 * - Uses no-referrer policy to avoid 429 errors
 * - Falls back to initials if image fails or unavailable
 * - Memoized to prevent unnecessary re-renders
 */
const UserAvatarComponent: React.FC<UserAvatarProps> = ({
  photoURL,
  displayName,
  email,
  size = 'md',
  className = ''
}) => {
  const [imageLoaded, setImageLoaded] = useState(true);
  const [imageError, setImageError] = useState(false);

  // Generate initials from display name or email
  const getInitials = () => {
    if (displayName) {
      return displayName
        .split(' ')
        .slice(0, 2)
        .map(n => n[0])
        .join('')
        .toUpperCase();
    }
    if (email) {
      return email.charAt(0).toUpperCase();
    }
    return 'U';
  };

  const initials = getInitials();
  const shouldShowImage = photoURL && !imageError;

  return (
    <div className={`${sizeClasses[size]} rounded-full border border-black/10 flex items-center justify-center font-bold overflow-hidden flex-shrink-0 ${className}`}>
      {shouldShowImage ? (
        <img
          src={photoURL}
          alt={displayName || email || 'User avatar'}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          crossOrigin="anonymous"
          className="w-full h-full object-cover"
          onLoad={() => setImageLoaded(true)}
          onError={(e) => {
            console.warn('Avatar image failed to load:', photoURL);
            setImageError(true);
            setImageLoaded(false);
          }}
          title={displayName || email || undefined}
        />
      ) : (
        <div className={`w-full h-full bg-flamingo text-white flex items-center justify-center font-bold`}>
          {initials}
        </div>
      )}
    </div>
  );
};

export const UserAvatar = React.memo(UserAvatarComponent);
UserAvatar.displayName = 'UserAvatar';

export default UserAvatar;
