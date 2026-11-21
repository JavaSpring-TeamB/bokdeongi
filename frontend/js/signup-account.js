const $ = (s) => document.querySelector(s);
const signupState = { account: null, consents: null, sensitiveCodes: null };
const workflow = { signupAttempted: false, accountCreated: false, authenticated: false, profileStep: 0, profileCompleted: false, submitting: false, completed: false, allowRecoveryNavigation: false };
const emailAvailability = { status: 'unchecked', checkedEmail: '', requestId: 0 };
const kakaoSignup = {
 requested: new URLSearchParams(window.location.search).get('kakaoSignup') === 'true',
 active: false,
 submitting: false,
 completed: false,
 retryRequired: false,
 outcomeUnknown: false,
 redirecting: false
};
const profileOnboarding = {
 requested: new URLSearchParams(window.location.search).get('profileOnboarding') === 'true',
 active: false,
 loading: false,
 profile: null
};
const MIN_BIRTH_DATE = '1900-01-01';
const today = () => {const now=new Date(),month=String(now.getMonth()+1).padStart(2,'0'),day=String(now.getDate()).padStart(2,'0');return `${now.getFullYear()}-${month}-${day}`;};
function notice(s, v) { const e=$(s); if(e){e.textContent=v;if(v)e.focus();} }
function field(n,v,valid=false){const e=$('#signup-'+n),m=$('#'+n+'-message');if(e){e.setAttribute('aria-invalid',String(Boolean(v)&&!valid));e.classList.toggle('is-valid',valid);}if(m){m.textContent=v;m.classList.toggle('is-valid',valid);}}
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const phonePattern = /^01[016789]\d{7,8}$/;
const REQUIRED_CONSENTS = ['TERMS', 'PRIVACY'];
const consentDocument = (type) => window.BokdeongiConsentDocuments?.get(type) || null;

function validateEmail(showEmpty=true){const input=$('#signup-email'),value=input.value.trim();if(!value){field('email',showEmpty?'이메일을 입력해 주세요.':'');return false;}if(value.length>100||!emailPattern.test(value)||!input.validity.valid){field('email','이메일 형식을 확인해 주세요.');return false;}field('email','이메일 형식이 올바릅니다.',true);return true;}
function currentEmail(){return $('#signup-email')?.value.trim()||'';}
function setEmailCheckButton(checking){const button=$('[data-email-check]');if(!button)return;button.disabled=checking;button.setAttribute('aria-busy',String(checking));}
function resetEmailAvailability(){emailAvailability.requestId+=1;emailAvailability.status='unchecked';emailAvailability.checkedEmail='';setEmailCheckButton(false);}
function hasAvailableEmail(){return emailAvailability.status==='available'&&emailAvailability.checkedEmail===currentEmail();}
function emailAvailabilityBlockMessage(){if(emailAvailability.status==='checking')return '이메일 중복 확인이 끝날 때까지 기다려 주세요.';if(emailAvailability.status==='duplicated')return '이미 가입된 이메일입니다. 다른 이메일을 입력해 주세요.';return '이메일 중복 확인을 해주세요.';}
async function checkEmailAvailability(){
 if(emailAvailability.status==='checking'||!validateEmail())return;
 const auth=window.BokdeongiAuth,email=currentEmail(),requestId=++emailAvailability.requestId;
 emailAvailability.status='checking';emailAvailability.checkedEmail=email;setEmailCheckButton(true);field('email','이메일 중복 여부를 확인하고 있어요.',true);
 try{
  if(!auth)throw new Error('authentication configuration is unavailable');
  const query=new URLSearchParams({email});
  const response=await fetch(auth.getApiUrl(`/api/auth/email-availability?${query.toString()}`),{credentials:'include'});
  const payload=await readJson(response);
  if(requestId!==emailAvailability.requestId||email!==currentEmail())return;
  if(!response.ok||!payload||typeof payload.available!=='boolean')throw new Error('invalid email availability response');
  emailAvailability.status=payload.available?'available':'duplicated';
  field('email',payload.available?'사용 가능한 이메일입니다.':'이미 가입된 이메일입니다. 다른 이메일을 입력해 주세요.',payload.available);
 }catch{
  if(requestId!==emailAvailability.requestId||email!==currentEmail())return;
  emailAvailability.status='error';
  field('email','이메일 중복 여부를 확인하지 못했습니다. 다시 시도해 주세요.');
 }finally{
  if(requestId===emailAvailability.requestId&&email===currentEmail())setEmailCheckButton(false);
 }
}
function validatePassword(showEmpty=true){const value=$('#signup-password').value;if(!value){field('password',showEmpty?'비밀번호를 입력해 주세요.':'');return false;}if(value.length<8||value.length>64){field('password','비밀번호는 8~64자로 입력해 주세요.');return false;}field('password','사용 가능한 비밀번호 길이입니다.',true);return true;}
function validatePasswordConfirm(showEmpty=true){const value=$('#signup-password-confirm').value,password=$('#signup-password').value;if(!value){field('password-confirm',showEmpty?'비밀번호를 다시 입력해 주세요.':'');return false;}if(value!==password){field('password-confirm','비밀번호가 일치하지 않습니다.');return false;}field('password-confirm','비밀번호가 일치합니다.',true);return true;}
function validateName(showEmpty=true){const input=$('#signup-name');input.value=input.value.trim();if(!input.value){field('name',showEmpty?'이름을 입력해 주세요.':'');return false;}if(input.value.length>50){field('name','이름은 50자 이내로 입력해 주세요.');return false;}field('name','',true);return true;}
function phoneDigits(value){return String(value||'').replace(/\D/g,'').slice(0,11);}
function formatPhoneNumber(value){const digits=phoneDigits(value);if(digits.length<=3)return digits;if(digits.length<=7)return `${digits.slice(0,3)}-${digits.slice(3)}`;if(digits.length<=10)return `${digits.slice(0,3)}-${digits.slice(3,6)}-${digits.slice(6)}`;return `${digits.slice(0,3)}-${digits.slice(3,7)}-${digits.slice(7)}`;}
function formatPhoneInput(input){const caret=input.selectionStart??input.value.length,digitCount=input.value.slice(0,caret).replace(/\D/g,'').length,formatted=formatPhoneNumber(input.value);input.value=formatted;let nextCaret=0,seen=0;for(;nextCaret<formatted.length&&seen<digitCount;nextCaret+=1){if(/\d/.test(formatted[nextCaret]))seen+=1;}input.setSelectionRange?.(nextCaret,nextCaret);}
function validatePhone(showEmpty=true){const input=$('#signup-phone'),digits=phoneDigits(input.value);if(!digits){field('phone',showEmpty?'연락처를 입력해 주세요.':'');return false;}if(!phonePattern.test(digits)){field('phone','국내 휴대전화 번호 형식을 확인해 주세요. 예: 010-1234-5678');return false;}field('phone','입력한 연락처 형식이 올바릅니다.',true);return true;}
function isCalendarDate(value){if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;const [year,month,day]=value.split('-').map(Number),date=new Date(year,month-1,day);return date.getFullYear()===year&&date.getMonth()===month-1&&date.getDate()===day;}
function validateBirthDate(showEmpty=true){const input=$('#signup-birth-date'),currentDate=today(),value=input.value;input.min=MIN_BIRTH_DATE;input.max=currentDate;if(!value){field('birth-date',showEmpty?'생년월일을 입력해 주세요.':'');return false;}if(!isCalendarDate(value)){field('birth-date','생년월일을 실제 날짜 형식으로 입력해 주세요.');return false;}if(value<MIN_BIRTH_DATE){field('birth-date','생년월일은 1900-01-01부터 입력해 주세요.');return false;}if(value>currentDate){field('birth-date','생년월일은 오늘 또는 이전 날짜로 입력해 주세요.');return false;}field('birth-date','',true);return true;}
function account(){const valid=[validateEmail(),validatePassword(),validatePasswordConfirm(),validateName(),validatePhone(),validateBirthDate()].every(Boolean);if(!valid)return null;return {email:$('#signup-email').value.trim(),password:$('#signup-password').value,name:$('#signup-name').value.trim(),phoneNumber:phoneDigits($('#signup-phone').value),birthDate:$('#signup-birth-date').value};}
function showStep(step){const target=document.querySelector(`[data-signup-step="${step}"]`);if(!target)return false;document.querySelectorAll('[data-signup-step]').forEach(section=>{section.hidden=section!==target;});if(step==='consent')syncAllConsent();if(step==='profile'){syncSensitiveProfile();void ensureSignupRegions();}target.querySelector('h2, h1, input, button')?.focus();return true;}
function setupAccount(){const form=$('#signup-account-form'),next=$('#signup-submit'),emailCheck=$('[data-email-check]');if(!form||!next)return;$('#signup-birth-date').min=MIN_BIRTH_DATE;$('#signup-birth-date').max=today();if(signupState.account){$('#signup-email').value=signupState.account.email;$('#signup-password').value=signupState.account.password||'';$('#signup-password-confirm').value=signupState.account.password||'';$('#signup-name').value=signupState.account.name;$('#signup-phone').value=formatPhoneNumber(signupState.account.phoneNumber);$('#signup-birth-date').value=signupState.account.birthDate;}
 const validators={email:validateEmail,password:validatePassword,passwordConfirm:validatePasswordConfirm,name:validateName,phone:validatePhone,birthDate:validateBirthDate};
 form.addEventListener('input',event=>{if(event.target.name==='phone')formatPhoneInput(event.target);if(event.target.name==='email'&&(emailAvailability.status!=='unchecked'||emailAvailability.checkedEmail!==currentEmail()))resetEmailAvailability();const validator=validators[event.target.name];if(!validator)return;validator(false);if(event.target.name==='password'&&$('#signup-password-confirm').value)validatePasswordConfirm(false);});
 form.addEventListener('blur',event=>{const validator=validators[event.target.name];if(validator)validator(true);},true);
 emailCheck?.addEventListener('click',()=>{void checkEmailAvailability();});
 const proceed=()=>{const value=account();if(!value){notice('#signup-form-message','필수 입력 항목과 입력 형식을 확인해 주세요.');return;}if(!hasAvailableEmail()){notice('#signup-form-message',emailAvailabilityBlockMessage());return;}notice('#signup-form-message','');signupState.account=value;showStep('consent');};
 next.addEventListener('click',proceed);form.addEventListener('submit',event=>{event.preventDefault();proceed();});
 form.querySelectorAll('[data-password-toggle]').forEach(button=>button.addEventListener('click',()=>{const input=$('#'+button.dataset.passwordToggle);if(!input)return;const visible=input.type==='text';input.type=visible?'password':'text';button.setAttribute('aria-pressed',String(!visible));button.setAttribute('aria-label',visible?'비밀번호 표시':'비밀번호 숨기기');button.textContent=visible?'◉':'◌';}));
 document.querySelector('[data-signup-step="consent"] [data-signup-prev="account"]')?.addEventListener('click',()=>showStep('account'));
}
function consentInputs(){return [...document.querySelectorAll('[data-signup-step="consent"] [data-consent-type]')];}
function captureConsents(){signupState.consents=consentInputs().map(input=>({consentType:input.dataset.consentType,docVersion:window.BokdeongiConsentDocuments?.versionFor(input.dataset.consentType)||null,agreed:input.checked}));}
function syncAllConsent(){const inputs=consentInputs(),all=$('#consent-all');if(!all)return;const allChecked=inputs.length>0&&inputs.every(input=>input.checked);all.checked=allChecked;all.indeterminate=!allChecked&&inputs.some(input=>input.checked);}
function setAllConsents(checked){consentInputs().forEach(input=>{input.checked=checked;});const all=$('#consent-all');if(all){all.checked=checked;all.indeterminate=false;}clearConsentError();captureConsents();syncSensitiveProfile();}
function clearConsentError(){const error=$('#consent-error');if(error)error.textContent='';consentInputs().forEach(input=>input.removeAttribute('aria-invalid'));}
function validateRequiredConsents(){const missing=REQUIRED_CONSENTS.filter(type=>!document.querySelector(`[data-signup-step="consent"] [data-consent-type="${type}"]`)?.checked);if(!missing.length){clearConsentError();return true;}const error=$('#consent-error');const labels=missing.map(type=>consentDocument(type)?.title||'필수 약관').join(', ');if(error){error.textContent=`필수 약관에 동의해 주세요: ${labels}.`;error.focus();}missing.forEach(type=>document.querySelector(`[data-signup-step="consent"] [data-consent-type="${type}"]`)?.setAttribute('aria-invalid','true'));return false;}
function setupConsent(){const form=$('#signup-consent-form'),all=$('#consent-all'),next=document.querySelector('[data-signup-step="consent"] [data-signup-next="profile"]'),inputs=consentInputs();if(!form||!all||!next)return;const syncAndCapture=()=>{syncAllConsent();clearConsentError();captureConsents();};all.addEventListener('change',()=>setAllConsents(all.checked));inputs.forEach(input=>input.addEventListener('change',syncAndCapture));const proceed=()=>{if(!validateRequiredConsents())return;captureConsents();if(kakaoSignup.active){submitKakaoSignup();return;}showStep('profile');};next.addEventListener('click',proceed);form.addEventListener('submit',event=>{event.preventDefault();proceed();});document.querySelector('[data-signup-step="profile"] [data-signup-prev="consent"]')?.addEventListener('click',()=>showStep('consent'));syncAllConsent();captureConsents();}

function clearKakaoSignupQuery(){window.history.replaceState(null,document.title,`${window.location.pathname}${window.location.hash}`);}
function clearProfileOnboardingQuery(){window.history.replaceState(null,document.title,`${window.location.pathname}${window.location.hash}`);}
function setKakaoSignupSubmitting(submitting,label='카카오 가입 완료 ›'){
 kakaoSignup.submitting=submitting;
 const next=document.querySelector('[data-signup-step="consent"] [data-signup-next="profile"]');
 const controls=[...consentInputs(),$('#consent-all')].filter(Boolean);
 controls.forEach(control=>{control.disabled=submitting;});
 if(next){next.disabled=submitting||kakaoSignup.completed||kakaoSignup.outcomeUnknown;next.textContent=submitting?'카카오 가입 처리 중…':label;}
}
function showKakaoRestart(){const restart=$('#kakao-signup-restart');if(restart)restart.hidden=false;}
function hideKakaoRestart(){const restart=$('#kakao-signup-restart');if(restart)restart.hidden=true;}
function showKakaoSignupFailure(message,unknown=false){
 kakaoSignup.retryRequired=true;
 kakaoSignup.outcomeUnknown=unknown;
 setKakaoSignupSubmitting(false);
 const next=document.querySelector('[data-signup-step="consent"] [data-signup-next="profile"]');
 if(next)next.disabled=true;
 notice('#consent-error',message);
 showKakaoRestart();
}
function showKakaoSignupComplete(){
 kakaoSignup.completed=true;
 setKakaoSignupSubmitting(false);
 hideKakaoRestart();
 const emailComplete=$('#email-signup-complete-content'),kakaoComplete=$('#kakao-signup-complete-content');
 if(emailComplete)emailComplete.hidden=true;
 if(kakaoComplete)kakaoComplete.hidden=false;
 showStep('complete');
}
function activateKakaoSignupMode(){
 if(kakaoSignup.active)return;
 kakaoSignup.active=true;
 clearKakaoSignupQuery();
 document.body.classList.add('is-kakao-signup');
 const previous=document.querySelector('[data-signup-step="consent"] [data-signup-prev="account"]');
 if(previous)previous.hidden=true;
 const accountSection=document.querySelector('[data-signup-step="account"]');
 if(accountSection)accountSection.hidden=true;
 const consentTitle=$('#consent-form-title');
 if(consentTitle)consentTitle.textContent='카카오 가입 약관 동의';
 const next=document.querySelector('[data-signup-step="consent"] [data-signup-next="profile"]');
 if(next)next.textContent='카카오 가입 완료 ›';
 showStep('consent');
}
function handleKakaoSignupAuthenticationState(status){
 if(!kakaoSignup.requested||kakaoSignup.active||kakaoSignup.completed)return;
 if(status==='checking')return;
 if(status==='unauthenticated'){activateKakaoSignupMode();return;}
 clearKakaoSignupQuery();
 if(status==='authenticated')notice('#signup-form-message','로그인된 계정이 있어 카카오 가입 절차를 시작하지 않았습니다. 현재 계정은 유지됩니다.');
 else notice('#signup-form-message','인증 상태를 확인하지 못해 카카오 가입 절차를 시작하지 않았습니다. 다시 로그인해 주세요.');
}
async function requestKakaoCsrf(auth){
 let response;
 try{response=await fetch(auth.getApiUrl('/api/auth/kakao/csrf'),{credentials:'include'});}catch{throw new SignupWorkflowError('kakao-csrf','카카오 가입을 위한 보안 확인에 실패했습니다. 카카오 로그인을 다시 시작해 주세요.');}
 const payload=await readJson(response);
 if(!response.ok||!payload||typeof payload.headerName!=='string'||typeof payload.token!=='string'||!payload.headerName||!payload.token)throw new SignupWorkflowError('kakao-csrf','카카오 가입을 위한 보안 확인에 실패했습니다. 카카오 로그인을 다시 시작해 주세요.',response.status);
 return payload;
}
function kakaoSignupErrorMessage(status){
 if(status===400)return '카카오 가입 정보를 확인하지 못했습니다. 카카오 로그인을 다시 시작해 주세요.';
 if(status===401)return '카카오 인증 세션을 확인하지 못했습니다. 카카오 로그인을 다시 시작해 주세요.';
 if(status===403)return '카카오 가입 보안 확인에 실패했습니다. 카카오 로그인을 다시 시작해 주세요.';
 if(status===409)return '가입 처리 중 계정 충돌이 발생했습니다. 기존 계정과 자동 연결하지 않았습니다. 카카오 로그인을 다시 시작해 주세요.';
 if(status>=500)return '가입 처리 중 서버 오류가 발생했습니다. 카카오 로그인을 다시 시작해 주세요.';
 return '카카오 가입을 완료하지 못했습니다. 카카오 로그인을 다시 시작해 주세요.';
}
async function submitKakaoSignup(){
 if(!kakaoSignup.active||kakaoSignup.submitting||kakaoSignup.completed||kakaoSignup.retryRequired||kakaoSignup.outcomeUnknown)return;
 const auth=window.BokdeongiAuth;
 if(!auth){showKakaoSignupFailure('인증 서버 설정을 확인하지 못했습니다. 카카오 로그인을 다시 시작해 주세요.');return;}
 setKakaoSignupSubmitting(true);
 hideKakaoRestart();
 let csrf;
 try{csrf=await requestKakaoCsrf(auth);}catch(error){showKakaoSignupFailure(error.message);return;}
 let response;
 try{
  response=await fetch(auth.getApiUrl('/api/auth/kakao/signup'),{method:'POST',credentials:'include',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({consents:signupState.consents})});
 }catch{
  showKakaoSignupFailure('가입 요청 결과를 확인하지 못했습니다. 중복 가입을 방지하기 위해 다시 전송하지 않습니다. 카카오 로그인을 다시 시작해 계정 상태를 확인해 주세요.',true);
  return;
 }
 const payload=await readJson(response);
 if(response.status!==201){showKakaoSignupFailure(kakaoSignupErrorMessage(response.status));return;}
 if(!payload||!Number.isFinite(payload.userId)){showKakaoSignupFailure('가입 처리 결과를 확인하지 못했습니다. 중복 가입을 방지하기 위해 다시 전송하지 않습니다. 카카오 로그인을 다시 시작해 계정 상태를 확인해 주세요.',true);return;}
 showKakaoSignupComplete();
}
function startKakaoOAuth(button){
 if(kakaoSignup.redirecting)return;
 const auth=window.BokdeongiAuth;
 if(!auth){notice('#consent-error','인증 서버 설정을 확인하지 못했습니다.');return;}
 let authorizationUrl;
 try{authorizationUrl=auth.getApiUrl('/oauth2/authorization/kakao');}catch{notice('#consent-error','인증 서버 설정을 확인하지 못했습니다.');return;}
 kakaoSignup.redirecting=true;
 if(button){button.disabled=true;button.setAttribute('aria-busy','true');}
 try{window.location.assign(authorizationUrl);}catch{if(button){button.disabled=false;button.setAttribute('aria-busy','false');}kakaoSignup.redirecting=false;notice('#consent-error','카카오 로그인을 시작하지 못했습니다. 다시 시도해 주세요.');}
}
function setupKakaoSignup(){
 $('#kakao-signup-restart')?.addEventListener('click',event=>startKakaoOAuth(event.currentTarget));
 $('#kakao-signup-relogin')?.addEventListener('click',event=>startKakaoOAuth(event.currentTarget));
 if(!kakaoSignup.requested)return;
 if(!window.BokdeongiAuth){handleKakaoSignupAuthenticationState('error');return;}
 handleKakaoSignupAuthenticationState(window.BokdeongiAuth.getAuthenticationState());
 window.addEventListener('bokdeongi:auth-state-change',event=>handleKakaoSignupAuthenticationState(event.detail?.status));
}
function profileOnboardingPayloadIsValid(profile){return profile&&typeof profile.profileCompleted==='boolean'&&Number.isInteger(Number(profile.profileStep))&&Number(profile.profileStep)>=0&&Number(profile.profileStep)<=3;}
function setChecked(name,value){const input=document.querySelector(`[name="${name}"][value="${value}"]`);if(input)input.checked=true;}
function setCodes(group,values){if(!Array.isArray(values))return;const selected=new Set(values);document.querySelectorAll(`[data-exclusive-group="${group}"] input`).forEach(input=>{input.checked=input.dataset.none!==undefined?selected.length===0:selected.has(input.value);});}
function restoreProfileRegion(profile){const sido=$('#profile-sido'),sigungu=$('#profile-sigungu'),region=window.BokdeongiRegions?.getData()?.findByCode(profile.regionCode);if(!sido||!sigungu||!region)return;sido.value=region.sidoName;populateSignupSigungu();sigungu.value=region.regionCode;}
function restoreProfileOnboarding(profile){
 const name=$('#profile-onboarding-name'),birthDate=$('#profile-onboarding-birth-date');
 if(name){name.value=typeof profile.name==='string'?profile.name:'';name.readOnly=Boolean(name.value);}
 if(birthDate)birthDate.value=typeof profile.birthDate==='string'?profile.birthDate:'';
 setChecked('gender',profile.gender);restoreProfileRegion(profile);
 if(profile.householdSize!==null&&profile.householdSize!==undefined)$('#profile-household-size').value=String(profile.householdSize);
 setChecked('housing_type',profile.housingType);if(typeof profile.hasHouse==='boolean')setChecked('has_house',String(profile.hasHouse));
 setChecked('income_input_type',profile.incomeInputType);setCodes('household',profile.householdTypeCodes);
 setCodes('status',profile.statusCodes);setChecked('marital_status',profile.maritalStatus);
 if(profile.childrenCount!==null&&profile.childrenCount!==undefined){const count=Number(profile.childrenCount);$('#profile-children-count').value=count>3?'4+':String(count);if(count>3)$('#profile-children-exact').value=String(count);}
 if(profile.youngestChildBirthYear!==null&&profile.youngestChildBirthYear!==undefined)$('#profile-youngest-year').value=String(profile.youngestChildBirthYear);
 setChecked('welfare_status',profile.welfareStatus);
 document.querySelectorAll('[name="income_input_type"], #profile-children-count').forEach(input=>input.dispatchEvent(new Event('change',{bubbles:true})));
 for(let step=1;step<=workflow.profileStep;step+=1)lockSavedProfileStep(step);
}
function activateProfileOnboarding(profile){
 if(profileOnboarding.active)return;
 profileOnboarding.active=true;profileOnboarding.profile=profile;workflow.accountCreated=true;workflow.authenticated=true;workflow.profileStep=Number(profile.profileStep);workflow.profileCompleted=false;
 clearProfileOnboardingQuery();document.body.classList.add('is-profile-onboarding');
 const accountSection=document.querySelector('[data-signup-step="account"]'),consentSection=document.querySelector('[data-signup-step="consent"]'),previous=document.querySelector('[data-signup-step="profile"] [data-signup-prev="consent"]');
 if(accountSection)accountSection.hidden=true;if(consentSection)consentSection.hidden=true;if(previous)previous.hidden=true;
 $('#profile-onboarding-name-field').hidden=false;$('#profile-onboarding-birth-date-field').hidden=false;
 const title=$('#profile-form-title');if(title)title.textContent='카카오 프로필 입력';
 const submit=$('#signup-complete-submit');if(submit)submit.textContent='프로필 저장 완료 ›';
 showStep('profile');void ensureSignupRegions().then(()=>restoreProfileOnboarding(profile));
}
function showProfileOnboardingError(message){clearProfileOnboardingQuery();showStep('account');notice('#signup-form-message',message);}
async function loadProfileOnboarding(status){
 if(!profileOnboarding.requested||profileOnboarding.active||profileOnboarding.loading)return;
 if(status==='checking')return;
 if(status!=='authenticated'||!window.BokdeongiAuth){showProfileOnboardingError('로그인 상태를 확인하지 못해 프로필 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');return;}
 profileOnboarding.loading=true;
 try{const response=await window.BokdeongiAuth.authenticatedFetch('/api/me/profile'),profile=await readJson(response);if(!response.ok||!profileOnboardingPayloadIsValid(profile)){showProfileOnboardingError('프로필 상태를 확인하지 못해 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');return;}if(profile.profileCompleted){clearProfileOnboardingQuery();window.location.replace('index.html');return;}activateProfileOnboarding(profile);}
 catch{showProfileOnboardingError('프로필 상태를 확인하지 못해 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');}
 finally{profileOnboarding.loading=false;}
}
function setupProfileOnboarding(){if(!profileOnboarding.requested)return;if(!window.BokdeongiAuth){showProfileOnboardingError('인증 서버 설정을 확인하지 못했습니다. 다시 로그인해 주세요.');return;}void loadProfileOnboarding(window.BokdeongiAuth.getAuthenticationState());window.addEventListener('bokdeongi:auth-state-change',event=>{void loadProfileOnboarding(event.detail?.status);});}
function signupRegionMessage(message){const target=$('#profile-region-message');if(target)target.textContent=message;}
function signupRegionErrorMessage(){return window.BokdeongiRegions?.getError()?.code==='empty'?'현재 선택 가능한 지역 정보가 없습니다.':'지역 목록을 불러오지 못했습니다. 다시 시도해 주세요.';}
function populateSignupSigungu(){const sido=$('#profile-sido'),sigungu=$('#profile-sigungu'),regions=window.BokdeongiRegions?.getData()?.selectableFor(sido?.value);if(!sido||!sigungu)return;if(!regions){sigungu.disabled=true;return;}const sejong=regions.length===1&&regions[0].regionCode==='36110'&&regions[0].sigunguName===null;if(sejong){sigungu.replaceChildren(new Option(regions[0].sidoName,regions[0].regionCode));sigungu.disabled=false;return;}sigungu.replaceChildren(new Option(regions.length?'시·군·구 선택':'선택 가능한 시·군·구가 없습니다.',''));regions.forEach(region=>sigungu.add(new Option(region.sigunguName||region.sidoName,region.regionCode)));sigungu.disabled=!regions.length;}
function renderSignupRegions(data){const sido=$('#profile-sido'),sigungu=$('#profile-sigungu');if(!sido||!sigungu)return;const selectedSido=sido.value,selectedCode=sigungu.value;sido.replaceChildren(new Option('시·도 선택',''));data.sidos.forEach(name=>sido.add(new Option(name,name)));sido.value=selectedSido;populateSignupSigungu();if(selectedCode&&[...sigungu.options].some(option=>option.value===selectedCode))sigungu.value=selectedCode;signupRegionMessage('');}
async function ensureSignupRegions(retry=false){const provider=window.BokdeongiRegions,sido=$('#profile-sido'),sigungu=$('#profile-sigungu'),retryButton=$('[data-signup-region-retry]');if(!provider){if(sido)sido.disabled=true;if(sigungu)sigungu.disabled=true;signupRegionMessage('지역 목록 설정을 확인하지 못했습니다.');return;}if(provider.getStatus()!=='ready'){if(sido)sido.disabled=true;if(sigungu)sigungu.disabled=true;signupRegionMessage('지역 목록을 불러오고 있어요.');}if(retryButton)retryButton.hidden=true;try{const data=await(retry?provider.retry():provider.load());renderSignupRegions(data);if(sido)sido.disabled=false;if(sigungu&&sido?.value)populateSignupSigungu();}catch{signupRegionMessage(signupRegionErrorMessage());if(retryButton)retryButton.hidden=false;}}
function setupProfileRegions(){const sido=$('#profile-sido'),retryButton=$('[data-signup-region-retry]');if(!sido)return;sido.addEventListener('change',()=>{populateSignupSigungu();signupRegionMessage('');});retryButton?.addEventListener('click',()=>{void ensureSignupRegions(true);});}
function sensitiveConsentGiven(){return document.querySelector('[data-signup-step="consent"] [data-consent-type="SENSITIVE"]')?.checked===true;}
function sensitiveInputs(){return [...document.querySelectorAll('#sensitive-section input[type="checkbox"]')];}
function captureSensitiveCodes(){if(!sensitiveConsentGiven()){signupState.sensitiveCodes=null;return;}const inputs=sensitiveInputs(),none=inputs.find(input=>input.dataset.none!==undefined),selected=inputs.filter(input=>input.dataset.sensitiveCode&&input.checked).map(input=>input.dataset.sensitiveCode);signupState.sensitiveCodes=none?.checked?[]:(selected.length?selected:null);}
function clearSensitiveCodes(){sensitiveInputs().forEach(input=>{input.checked=false;});signupState.sensitiveCodes=null;}
function syncSensitiveProfile(){const section=$('#sensitive-section'),noticeTarget=$('#sensitive-notice');if(!section||!noticeTarget)return;const allowed=sensitiveConsentGiven();if(!allowed)clearSensitiveCodes();section.classList.toggle('is-disabled',!allowed);section.setAttribute('aria-disabled',String(!allowed));sensitiveInputs().forEach(input=>{input.disabled=!allowed;});noticeTarget.hidden=allowed;if(allowed)captureSensitiveCodes();}
function setupSensitiveProfile(){const section=$('#sensitive-section'),sensitiveConsent=document.querySelector('[data-signup-step="consent"] [data-consent-type="SENSITIVE"]');if(!section||!sensitiveConsent)return;section.addEventListener('change',event=>{const input=event.target;if(!input.matches('input[type="checkbox"]'))return;const inputs=sensitiveInputs(),none=inputs.find(item=>item.dataset.none!==undefined);if(input.dataset.none!==undefined&&input.checked)inputs.filter(item=>item.dataset.sensitiveCode).forEach(item=>{item.checked=false;});else if(input.checked&&none)none.checked=false;captureSensitiveCodes();});sensitiveConsent.addEventListener('change',syncSensitiveProfile);syncSensitiveProfile();}
function setupConsentDialog(){const dialog=$('#consent-dialog'),title=$('#consent-dialog-title'),content=$('#consent-dialog-content');if(!dialog||!title||!content)return;let opener=null;document.addEventListener('click',event=>{const viewButton=event.target.closest('[data-consent-view]');if(viewButton){const type=viewButton.dataset.consentView,info=consentDocument(type);if(!info){notice('#consent-error','동의 문서를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.');return;}opener=viewButton;title.textContent=info.title;window.BokdeongiConsentDocuments.renderInto(content,type);dialog.showModal();return;}if(event.target.closest('[data-dialog-close]'))dialog.close();});dialog.addEventListener('close',()=>opener?.focus());}
function radio(n){return document.querySelector(`[name="${n}"]:checked`)?.value||'';} function codes(g){const a=[...document.querySelectorAll(`[data-exclusive-group="${g}"] input:checked`)];return !a.length?null:a.some(i=>i.dataset.none!==undefined)?[]:[...new Set(a.map(i=>i.value))];}
function profile(){
 const onboarding=profileOnboarding.active,birthDate=onboarding?$('#profile-onboarding-birth-date').value:signupState.account?.birthDate||'',name=onboarding?$('#profile-onboarding-name').value.trim():signupState.account?.name||'',gender=radio('gender'),sido=$('#profile-sido'),sigungu=$('#profile-sigungu'),householdSize=Number($('#profile-household-size').value),housingType=radio('housing_type'),hasHouse=radio('has_house'),incomeInputType=radio('income_input_type'),maritalStatus=radio('marital_status'),childrenChoice=$('#profile-children-count').value,householdTypeCodes=codes('household'),statusCodes=codes('status'),welfareStatus=radio('welfare_status');
 const needsStep1=workflow.profileStep<1,needsStep2=workflow.profileStep<2,needsStep3=workflow.profileStep<3;
 if((needsStep1&&(!isCalendarDate(birthDate)||birthDate<MIN_BIRTH_DATE||birthDate>today()||!gender||!sido.value||!sigungu.value||(onboarding&&!name)||name.length>50))||(needsStep2&&(!householdSize||!housingType||!hasHouse||!incomeInputType||householdTypeCodes===null))||(needsStep3&&(!maritalStatus||!childrenChoice||statusCodes===null||!welfareStatus))){notice('#profile-form-error','필수 프로필 항목을 확인해 주세요.');return null;}
 const usesIncomeAmount=['ANNUAL','MONTHLY'].includes(incomeInputType),incomeAmountValue=$('#profile-income-amount').value;
 if(needsStep2&&usesIncomeAmount&&(incomeAmountValue===''||!Number.isFinite(Number(incomeAmountValue))||Number(incomeAmountValue)<0)){notice('#profile-form-error','0원 이상의 소득 금액을 입력해 주세요.');return null;}
 const childrenCount=childrenChoice==='4+'?Number($('#profile-children-exact').value):Number(childrenChoice);
 if(needsStep3&&(!Number.isInteger(childrenCount)||childrenCount<0||childrenCount>32767||(childrenChoice==='4+'&&childrenCount<4))){notice('#profile-form-error','자녀 수를 확인해 주세요.');return null;}
 const youngestValue=$('#profile-youngest-year').value,youngestChildBirthYear=youngestValue===''?null:Number(youngestValue),currentYear=new Date().getFullYear(),memberBirthYear=Number(birthDate.slice(0,4));
 if(needsStep3&&childrenCount>0&&(youngestChildBirthYear===null||!Number.isInteger(youngestChildBirthYear)||youngestChildBirthYear<1900||youngestChildBirthYear>currentYear||youngestChildBirthYear<memberBirthYear)){notice('#profile-form-error','막내 자녀 출생연도를 확인해 주세요.');return null;}
 notice('#profile-form-error','');
 return [
  {birthDate,gender,regionCode:sigungu.value,sidoName:sido.value,sigunguName:sigungu.options[sigungu.selectedIndex].text,name:name||null},
  {householdSize,incomeInputType,incomeAmount:usesIncomeAmount?Number(incomeAmountValue):null,medianIncomePct:null,housingType,noHouse:hasHouse==='false',householdTypeCodes},
  {statusCodes,maritalStatus,childrenCount,youngestChildBirthYear:childrenCount===0?null:youngestChildBirthYear,welfareStatus}
 ];
}

class SignupWorkflowError extends Error {
 constructor(stage,message,status=0,payload=null,uncertain=false){super(message);this.stage=stage;this.status=status;this.payload=payload;this.uncertain=uncertain;}
}

async function readJson(response){try{return await response.json();}catch{return null;}}
function responseMessage(payload,fallback){if(payload&&typeof payload.message==='string'&&payload.message.trim())return payload.message.trim();const reason=payload?.fieldErrors?.find(error=>error&&typeof error.reason==='string')?.reason;return reason||fallback;}
function clearSignupPasswords(){if(signupState.account)signupState.account.password='';const password=$('#signup-password'),confirmation=$('#signup-password-confirm');if(password)password.value='';if(confirmation)confirmation.value='';}
function showRecoveryLink(href,label){const link=$('#signup-login-recovery');if(!link)return;link.href=href;link.textContent=label;link.hidden=false;}
function hideRecoveryLink(){const link=$('#signup-login-recovery');if(link)link.hidden=true;}
function lockCreatedAccountSteps(){document.querySelectorAll('#signup-account-form input, #signup-account-form button, #signup-consent-form input, #signup-consent-form button').forEach(control=>{control.disabled=true;});const previous=document.querySelector('[data-signup-step="profile"] [data-signup-prev="consent"]');if(previous)previous.disabled=true;}
function lockSavedProfileStep(step){document.querySelectorAll(`[data-profile-request-step="${step}"] input, [data-profile-request-step="${step}"] select, [data-profile-request-step="${step}"] button`).forEach(control=>{control.disabled=true;});}
function setWorkflowSubmitting(submitting,label='회원가입 완료 ›'){workflow.submitting=submitting;const button=$('#signup-complete-submit'),previous=document.querySelector('[data-signup-step="profile"] [data-signup-prev="consent"]');if(button){const blocked=(workflow.signupAttempted&&!workflow.accountCreated)||(workflow.accountCreated&&!workflow.authenticated),retryLabel=workflow.profileStep===3&&!workflow.profileCompleted?'프로필 완료 다시 시도':'프로필 저장 다시 시도';button.disabled=submitting||workflow.completed||blocked;button.textContent=submitting?label:(workflow.accountCreated?retryLabel:'회원가입 완료 ›');}if(previous)previous.disabled=submitting||workflow.accountCreated||workflow.signupAttempted;}

async function createAccount(){
 if(workflow.accountCreated)return;
 if(workflow.signupAttempted)throw new SignupWorkflowError('signup-unknown','이전 가입 요청 결과를 확인할 수 없습니다. 생성된 계정으로 로그인을 시도해 주세요.',0,null,true);
 const auth=window.BokdeongiAuth;
 if(!auth)throw new SignupWorkflowError('configuration','인증 서버 설정을 불러오지 못했습니다.');
 workflow.signupAttempted=true;
 let response;
 try{response=await fetch(auth.getApiUrl('/api/auth/signup'),{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({...signupState.account,consents:signupState.consents})});}
 catch{throw new SignupWorkflowError('signup-unknown','가입 요청 결과를 확인할 수 없습니다. 중복 요청하지 말고 로그인 화면에서 계정 생성 여부를 확인해 주세요.',0,null,true);}
 const payload=await readJson(response);
 if(response.status!==201){workflow.signupAttempted=false;const duplicate=response.status===409||payload?.code==='AUTH_001'||String(payload?.message||'').includes('이미 사용 중인 이메일');throw new SignupWorkflowError('signup',duplicate?'이미 사용 중인 이메일입니다. 다른 이메일을 입력해 주세요.':responseMessage(payload,'회원가입 정보를 확인해 주세요.'),response.status,payload);}
 workflow.accountCreated=true;
 lockCreatedAccountSteps();
 if(!payload||!Number.isFinite(payload.userId)||typeof payload.email!=='string')throw new SignupWorkflowError('signup-response','계정은 생성되었지만 서버 응답을 확인하지 못했습니다. 로그인 화면에서 가입 여부를 확인해 주세요.',response.status,payload);
}

async function loginCreatedAccount(){
 if(workflow.authenticated)return;
 const auth=window.BokdeongiAuth,password=signupState.account?.password;
 if(!auth||!password)throw new SignupWorkflowError('login','계정은 생성되었지만 자동 로그인 정보를 사용할 수 없습니다. 로그인 화면에서 로그인해 주세요.');
 try{
  let response;
  try{response=await fetch(auth.getApiUrl('/api/auth/login'),{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:signupState.account.email,password})});}
  catch{throw new SignupWorkflowError('login','계정은 생성되었지만 로그인 서버에 연결할 수 없습니다. 로그인 화면에서 다시 시도해 주세요.');}
  const payload=await readJson(response);
  if(!response.ok)throw new SignupWorkflowError('login',responseMessage(payload,'계정은 생성되었지만 자동 로그인에 실패했습니다. 로그인 화면에서 다시 시도해 주세요.'),response.status,payload);
  if(!auth.completeLogin(payload))throw new SignupWorkflowError('login','계정은 생성되었지만 로그인 응답을 확인하지 못했습니다. 로그인 화면에서 다시 시도해 주세요.',response.status,payload);
  workflow.authenticated=true;
 }finally{clearSignupPasswords();}
}

async function saveProfileSteps(requests){
 const auth=window.BokdeongiAuth;
 for(let index=workflow.profileStep;index<requests.length;index+=1){
  const step=index+1;
  let response;
  try{const csrf=await auth.getCsrfToken();response=await auth.authenticatedFetch(`/api/me/profile/step${step}`,{method:'PUT',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(requests[index])});}
  catch{if(auth.getAuthenticationState()!=='authenticated')workflow.authenticated=false;throw new SignupWorkflowError('profile',`프로필 ${step}단계 저장 요청을 완료하지 못했습니다.`,0,{step});}
  const payload=await readJson(response);
  if(!response.ok){if(response.status===401)workflow.authenticated=false;throw new SignupWorkflowError('profile',responseMessage(payload,`프로필 ${step}단계 저장에 실패했습니다.`),response.status,{...payload,step});}
  const responseStep=Number(payload?.profileStep);
  if(!Number.isFinite(responseStep)||responseStep<step)throw new SignupWorkflowError('profile',`프로필 ${step}단계 저장 응답을 확인하지 못했습니다.`,response.status,{step});
  workflow.profileStep=step;
  lockSavedProfileStep(step);
 }
}

function hasCompletedProfile(payload){const completedAt=payload?.profileCompletedAt;return payload?.profileCompleted===true&&Number(payload.profileStep)>=3&&typeof completedAt==='string'&&completedAt.trim().length>0;}
async function confirmProfileCompletion(){
 const auth=window.BokdeongiAuth;
 try{
  const response=await auth.authenticatedFetch('/api/me/profile');
  const payload=await readJson(response);
  if(response.ok&&hasCompletedProfile(payload)){workflow.profileCompleted=true;return true;}
  if(response.status===401)workflow.authenticated=false;
 }catch{if(auth.getAuthenticationState()!=='authenticated')workflow.authenticated=false;}
 return false;
}
async function completeProfile(){
 if(workflow.profileCompleted)return;
 const auth=window.BokdeongiAuth;
 let response;
 try{
  const csrf=await auth.getCsrfToken();
  response=await auth.authenticatedFetch('/api/me/profile/complete',{method:'PUT',headers:{[csrf.headerName]:csrf.token}});
 }catch{
  if(await confirmProfileCompletion())return;
  throw new SignupWorkflowError('profile-complete','프로필 완료 요청 결과를 확인하지 못했습니다. 완료 처리만 다시 시도해 주세요.');
 }
 const payload=await readJson(response);
 if(!response.ok){if(response.status===401)workflow.authenticated=false;if(response.status>=500&&await confirmProfileCompletion())return;throw new SignupWorkflowError('profile-complete',responseMessage(payload,'프로필 완료 처리에 실패했습니다.'),response.status,payload);}
 if(!hasCompletedProfile(payload)){if(await confirmProfileCompletion())return;throw new SignupWorkflowError('profile-complete','서버 응답에서 프로필 완료 상태를 확인하지 못했습니다.',response.status,payload);}
 workflow.profileCompleted=true;
}

function finishSignup(){if(!workflow.profileCompleted)throw new SignupWorkflowError('profile-complete','서버에서 프로필 완료 상태를 확인하지 못했습니다.');workflow.completed=true;clearSignupPasswords();clearSensitiveCodes();if(profileOnboarding.active){window.location.replace('index.html');return;}const pendingSensitive=Array.isArray(signupState.sensitiveCodes)&&signupState.sensitiveCodes.length>0,noticeTarget=$('#signup-sensitive-pending-notice');if(noticeTarget)noticeTarget.hidden=!pendingSensitive;if(!showStep('complete'))throw new SignupWorkflowError('completion','계정과 프로필은 저장되었지만 완료 화면을 표시하지 못했습니다.');}

function handleWorkflowError(error){
 const failure=error instanceof SignupWorkflowError?error:new SignupWorkflowError('unknown','회원가입 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.');
 if(profileOnboarding.active){showStep('profile');notice('#profile-form-error',failure.message);if(!workflow.authenticated)showRecoveryLink('login.html','다시 로그인하기');return;}
 if(failure.stage==='signup'&&!workflow.accountCreated){showStep('account');notice('#signup-form-message',failure.message);return;}
 showStep('profile');
 if(failure.stage==='signup-unknown'){clearSignupPasswords();notice('#profile-form-error',failure.message);showRecoveryLink('login.html','계정 생성 여부를 로그인으로 확인하기');return;}
 if(workflow.accountCreated&&!workflow.authenticated){clearSignupPasswords();notice('#profile-form-error',failure.message.startsWith('계정은 생성')?failure.message:`계정은 생성되었습니다. ${failure.message}`);showRecoveryLink('login.html','생성된 계정으로 로그인하기');return;}
 if(failure.stage==='profile'){const step=failure.payload?.step||workflow.profileStep+1;notice('#profile-form-error',`계정 생성과 로그인은 완료되었습니다. 프로필 ${step}단계부터 다시 시도해 주세요. ${failure.message}`);showRecoveryLink('my-page.html','마이페이지에서 프로필 보완하기');return;}
 if(failure.stage==='profile-complete'){notice('#profile-form-error',`계정 생성과 프로필 1~3단계 저장은 완료되었습니다. ${failure.message}`);showRecoveryLink('my-page.html','마이페이지에서 완료 상태 확인하기');return;}
 notice('#profile-form-error',failure.message);
 if(workflow.profileStep===3)showRecoveryLink('my-page.html','마이페이지로 이동하기');
}

async function runSignupWorkflow(){
 if(workflow.submitting||workflow.completed||(workflow.signupAttempted&&!workflow.accountCreated))return;
 hideRecoveryLink();
 if(profileOnboarding.active){const requests=profile();if(!requests)return;setWorkflowSubmitting(true,workflow.profileStep<3?`프로필 ${workflow.profileStep+1}단계 저장 중…`:'프로필 완료 처리 중…');try{if(workflow.profileStep<requests.length)await saveProfileSteps(requests);setWorkflowSubmitting(true,'프로필 완료 처리 중…');await completeProfile();finishSignup();}catch(error){handleWorkflowError(error);}finally{setWorkflowSubmitting(false);}return;}
 if(!workflow.accountCreated){const accountValue=account();if(!accountValue){showStep('account');notice('#signup-form-message','필수 입력 항목과 입력 형식을 확인해 주세요.');return;}signupState.account=accountValue;if(!validateRequiredConsents()){showStep('consent');return;}captureConsents();}
 const requests=profile();if(!requests)return;
 captureSensitiveCodes();
 setWorkflowSubmitting(true,'계정 생성 중…');
 try{await createAccount();setWorkflowSubmitting(true,'자동 로그인 중…');await loginCreatedAccount();if(workflow.profileStep<requests.length){setWorkflowSubmitting(true,`프로필 ${workflow.profileStep+1}단계 저장 중…`);await saveProfileSteps(requests);}setWorkflowSubmitting(true,'프로필 완료 처리 중…');await completeProfile();finishSignup();}
 catch(error){handleWorkflowError(error);}
 finally{setWorkflowSubmitting(false);}
}

function setupProfileForm(){
 const form=$('#signup-profile-form'),incomeFields=$('#income-amount-fields'),incomeAmount=$('#profile-income-amount'),children=$('#profile-children-count'),exact=$('#exact-children-field'),youngest=$('#youngest-child-field');
 const updateIncome=()=>{const needsAmount=['ANNUAL','MONTHLY'].includes(radio('income_input_type'));incomeFields.hidden=!needsAmount;incomeAmount.required=needsAmount;if(!needsAmount)incomeAmount.value='';};
 const updateChildren=()=>{const value=children.value,hasChildren=Boolean(value&&value!=='0');exact.hidden=value!=='4+';youngest.hidden=!hasChildren;if(value!=='4+')$('#profile-children-exact').value='';if(!hasChildren)$('#profile-youngest-year').value='';};
 document.querySelectorAll('[name="income_input_type"]').forEach(input=>input.addEventListener('change',updateIncome));children.addEventListener('change',updateChildren);
 document.querySelectorAll('[data-exclusive-group="household"], [data-exclusive-group="status"]').forEach(group=>group.addEventListener('change',event=>{const input=event.target;if(!input.matches('input[type="checkbox"]'))return;const none=group.querySelector('[data-none]');if(input.dataset.none!==undefined&&input.checked)group.querySelectorAll('input:not([data-none])').forEach(item=>{item.checked=false;});else if(input.checked&&none)none.checked=false;}));
 form.addEventListener('input',()=>notice('#profile-form-error',''));
 form.addEventListener('submit',event=>{event.preventDefault();runSignupWorkflow();});
 $('#signup-login-recovery')?.addEventListener('click',()=>{workflow.allowRecoveryNavigation=true;clearSensitiveCodes();});
 window.addEventListener('beforeunload',event=>{if(!workflow.accountCreated||workflow.completed||workflow.allowRecoveryNavigation)return;event.preventDefault();event.returnValue='';});
 updateIncome();updateChildren();setWorkflowSubmitting(false);
}

document.addEventListener('DOMContentLoaded',()=>{setupAccount();setupConsent();setupConsentDialog();setupProfileRegions();setupSensitiveProfile();setupProfileForm();setupKakaoSignup();setupProfileOnboarding();void ensureSignupRegions();});
