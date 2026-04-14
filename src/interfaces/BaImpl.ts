export interface SchoolImpl {
  id: string;
  cn_name: string;
  jp_name: string | null;
  kr_name: string | null;
  en_name: string | null;
  introduce: string | null;
  logo: string | null;
  beautify_logo: string | null;
  bg: string | null;
}

export interface ClubImpl {
  id: string;
  cn_name: string;
  jp_name: string | null;
  kr_name: string | null;
  en_name: string | null;
  logo: string | null;
  bg: string | null;
}

export interface StudentImpl {
  id: string;
  cn_name: string;
  jp_name: string | null;
  kr_name: string | null;
  en_name: string;
  introduce: string | null;
  avatar_square: string | null;
  avatar_rectangle: string;
  body_image: string;
  school: string;
  club: string;
}

export interface SchoolClubImpl {
  id: number;
  school_id: string;
  club_id: string;
}
