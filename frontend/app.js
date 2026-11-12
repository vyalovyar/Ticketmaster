const DEV_SERVER_PORTS = ['5500', '3000', '5173', '8000', '8080'];
function resolveApiBase() {
  const override = window.__API_BASE__;
  if (typeof override === 'string' && override.trim()) return override.trim().replace(/\/+$/, '');
  const { hostname, port } = window.location;
  const isLoopback = ['localhost', '127.0.0.1', '[::1]'].includes(hostname);
  if (isLoopback && DEV_SERVER_PORTS.includes(port)) return 'http://localhost:8080';
  return '';
}
const API_BASE = resolveApiBase();
const BOOKING_API_BASE = API_BASE;
const USER_API_BASE = API_BASE;

let allEvents = [];
let selectedEvent = null;
let selectedSeats = [];
let currentUser = null;
let adminEvents = [];
let editingEventId = null;
let authMode = 'login';
let ownedSeats = new Map();
const EVENTS_PER_PAGE = 8;
let currentPage = 1;
const seatsBeingPaid = new Map();
const MAX_SEATS = 500;

let currentLang = localStorage.getItem('pulsepass-lang') || 'ru';
let activeFilter = 'all';
let lastEmptyKey = 'events.empty';
let countdownTimer = null;
let countdownTarget = 0;
let statsAnimated = false;
let statsVisible = false;
const statTargets = { events: 0, cities: 0, seats: 0 };
const statAnimTokens = { statEvents: 0, statCities: 0, statSeats: 0 };
let dashboardProfile = null;
let dashboardOrders = [];
let dashProfileLoaded = false;
let dashOrdersLoaded = false;
let activeDashTab = 'upcoming';
let pendingReturn = null;
let marqueeResizeTimer = null;

const I18N = {
  ru: {
    'doc.title': 'PulsePass — билеты на концерты и живые события',
    'nav.events': 'События', 'nav.how': 'Как это работает', 'nav.ai': 'AI-подбор', 'nav.faq': 'FAQ', 'nav.signin': 'Войти',
    'hero.eyebrow': 'Живые события',
    'hero.title': 'Найдите свой следующий незабываемый вечер.',
    'hero.subtitle': 'Поиск концертов, интерактивная схема зала и покупка билета за пару минут. Без очередей и лишних страниц.',
    'search.artist': 'Артист', 'search.artistPh': 'Найти артиста', 'search.location': 'Город', 'search.locationPh': 'Город или площадка', 'search.date': 'Дата', 'search.submit': 'Найти',
    'stats.events': 'событий в афише', 'stats.cities': 'городов', 'stats.seats': 'свободных мест',
    'next.days': 'дней', 'next.hours': 'часов', 'next.minutes': 'мин', 'next.seconds': 'сек', 'next.choose': 'Выбрать места',
    'events.title': 'Ближайшие события',
    'filter.all': 'Все', 'filter.soonest': 'Сначала ближайшие', 'filter.cheapest': 'Сначала дешевле', 'filter.weekend': 'На выходных',
    'card.buy': 'Выбрать места', 'card.from': 'от', 'card.soldout': 'Билетов нет',
    'events.empty': 'Мероприятий пока нет.', 'events.noResults': 'Ничего не найдено. Попробуйте изменить запрос.', 'events.loadError': 'Не удалось загрузить мероприятия. Проверьте доступность API.',
    'how.title': 'Как это работает',
    'how.s1t': 'Найдите событие', 'how.s1d': 'Ищите по артисту, городу и дате — или опишите пожелания AI-ассистенту.',
    'how.s2t': 'Выберите место', 'how.s2d': 'Схема зала показывает свободные и занятые места в реальном времени. Можно взять несколько билетов сразу.',
    'how.s3t': 'Оплатите — и вперёд', 'how.s3d': 'Билет появится в личном кабинете, а при изменении планов его можно вернуть.',
    'ai.title': 'AI-подбор',
    'ai.hint': 'Опишите, что хотите: «концерт в Берлине» или «что-нибудь из джаза» — ассистент предложит события с ценами.',
    'ai.placeholder': 'Например: рок-концерт в Берлине', 'ai.ask': 'Спросить',
    'ai.needAuth': 'Чтобы пользоваться AI-ассистентом, войдите в аккаунт.', 'ai.error': 'Ассистент сейчас недоступен.', 'ai.from': 'от',
    'ai.ex1': 'Хочу на концерт в Берлине', 'ai.ex2': 'Что-нибудь из джаза', 'ai.ex3': 'Рок-концерт на выходных',
    'faq.title': 'Частые вопросы',
    'q1': 'Нужно ли регистрироваться, чтобы купить билет?', 'a1': 'Да, аккаунт нужен — так мы сохраняем ваши билеты и историю покупок. Регистрация занимает меньше минуты.',
    'q2': 'Можно ли вернуть билет?', 'a2': 'Да. В личном кабинете у каждого билета есть кнопка «Вернуть билет» — место снова станет доступным, а заказ отменится.',
    'q3': 'Как понять, какие места заняты?', 'a3': 'Схема зала синхронизируется с сервером: белые места свободны, серые — заняты или в процессе оплаты.',
    'q4': 'Что умеет AI-ассистент?', 'a4': 'Опишите запрос обычными словами — город, жанр, настроение — и он предложит подходящие события с ценами.',
    'q5': 'Это настоящий платёжный сервис?', 'a5': 'Нет: PulsePass — демо-проект. Платежи имитируются, данные карт не сохраняются и никуда не отправляются.',
    'cta.title': 'Ваш вечер начинается здесь', 'cta.text': 'Создайте аккаунт — билеты, история покупок и AI-подбор всегда под рукой.',
    'cta.signup': 'Создать аккаунт', 'cta.browse': 'Смотреть афишу',
    'footer.tagline': 'Живые впечатления и простая покупка билетов.', 'footer.explore': 'Разделы',
    'footer.rights': '© {year} PulsePass — демо-проект. Билеты и платежи имитируются.',
    'common.close': 'Закрыть',
    'auth.showPw': 'Показать пароль',
    'auth.hidePw': 'Скрыть пароль',
    'events.upcoming': 'Скоро',
    'toast.seatTaken': 'Место {seat} только что заняли',
    'stub.title': 'Это заглушка', 'stub.text': 'Кнопка ненастоящая: функция не подключена в демо-версии.',
    'auth.signin': 'Вход', 'auth.register': 'Регистрация', 'auth.fullName': 'Имя и фамилия', 'auth.fullNamePh': 'Иван Петров',
    'auth.email': 'Email', 'auth.emailPh': 'you@example.com', 'auth.password': 'Пароль', 'auth.passwordPh': '••••••••', 'auth.confirm': 'Повторите пароль',
    'auth.forgot': 'Забыли пароль?', 'auth.or': 'или войти через', 'auth.yandex': 'Яндекс ID', 'auth.sber': 'Сбер ID',
    'auth.submitLogin': 'Войти', 'auth.submitRegister': 'Создать аккаунт', 'auth.creating': 'Создаём аккаунт…',
    'auth.welcome': 'С возвращением!', 'auth.registered': 'Регистрация успешна. Теперь можно покупать билеты.',
    'auth.authFailed': 'Не удалось выполнить вход.', 'auth.mismatch': 'Пароли не совпадают', 'auth.enterEmail': 'Введите корректный email',
    'auth.enterName': 'Введите имя (минимум 2 символа)', 'auth.shortPw': 'Пароль — минимум 6 символов', 'auth.needPurchase': 'Войдите, чтобы купить билеты.',
    'auth.sideTitle': 'Ваш доступ к лучшим вечерам', 'auth.sideText': 'Один аккаунт — билеты, возвраты и AI-подбор событий.',
    'auth.f1': 'Билеты всегда под рукой', 'auth.f2': 'Возврат в один клик', 'auth.f3': 'AI-подбор событий',
    'auth.pwHint': 'надёжность пароля', 'auth.pw0': 'слабый', 'auth.pw1': 'средний', 'auth.pw2': 'хороший', 'auth.pw3': 'надёжный',
    'pay.title': 'Оплата', 'pay.name': 'Имя владельца карты', 'pay.namePh': 'IVAN PETROV', 'pay.number': 'Номер карты',
    'pay.expiry': 'Срок', 'pay.cvc': 'CVC', 'pay.ticket': 'Выбранные билеты', 'pay.pay': 'Оплатить', 'pay.processing': 'Обработка…',
    'event.selectSeat': 'Выберите места', 'event.noSeat': 'Место не выбрано', 'event.stage': 'Сцена', 'event.close': 'Закрыть',
    'event.buy': 'Купить билет', 'event.buyN': 'Купить билеты', 'event.source': 'Подробнее на Ticketmaster ↗',
    'legend.free': 'Свободно', 'legend.selected': 'Выбрано', 'legend.booked': 'Занято / оплата',
    'dash.back': '← На главную', 'dash.hello': 'Личный кабинет', 'dash.logout': 'Выйти', 'dash.since': 'с нами с',
    'dash.tickets': 'Мои билеты', 'dash.upcoming': 'Предстоящие', 'dash.past': 'Прошедшие',
    'dash.statTickets': 'билетов всего', 'dash.statUpcoming': 'предстоящих', 'dash.statSpent': 'потрачено',
    'dash.empty': 'Пока нет заказов. После покупки билеты появятся здесь.', 'dash.emptyPast': 'Прошедших событий пока нет.',
    'dash.seatRow': 'Ряд', 'dash.seatNo': 'место', 'dash.return': 'Вернуть билет',
    'dash.notFoundT': 'Мероприятие не найдено', 'dash.notFoundD': 'Не удалось найти событие для возврата.',
    'dash.findEvents': 'Найти события', 'dash.retry': 'Повторить',
    'dash.profileErr': 'Не удалось загрузить профиль.', 'dash.ordersErr': 'Не удалось загрузить билеты.',
    'dash.confirmTitle': 'Вернуть билет?', 'dash.confirmCancel': 'Отмена',
    'dash.changePhoto': 'Изменить фото', 'dash.removePhoto': 'Удалить фото', 'dash.avatarAlt': 'Фото профиля',
    'dash.adminBadge': 'Администратор', 'dash.order': 'Заказ №',
    'dash.loggedOut': 'Вы вышли из аккаунта',
    'dash.avatarBadType': 'Неверный тип файла', 'dash.avatarBadTypeD': 'Поддерживаются PNG, JPEG и WebP.',
    'dash.avatarTooLarge': 'Файл слишком большой', 'dash.avatarTooLargeD': 'Максимальный размер — 5 МБ.',
    'dash.avatarFail': 'Не удалось сохранить фото', 'dash.avatarFailD': 'Попробуйте другое изображение.',
    'dash.avatarSaved': 'Фото профиля обновлено', 'dash.avatarSavedD': 'Хранится в этом браузере (демо).',
    'toast.payOkT': 'Платёж успешно завершён', 'toast.payOkD': 'Билет подтверждён.',
    'toast.payFailT': 'Платёж не прошёл', 'toast.payFailD': 'Проверьте данные карты и попробуйте ещё раз.',
    'toast.retOkT': 'Билет возвращён', 'toast.retOkD': 'место снова доступно.',
    'toast.retFailT': 'Возврат не выполнен', 'toast.retFailD': 'Билет не удалось вернуть сейчас.',
    'toast.yourSeat': 'Ваш билет', 'toast.booked': 'Забронировано', 'toast.payingSeat': 'Оплата в процессе',
    'admin.manage': 'Управление', 'admin.new': 'Новое событие', 'admin.import': 'Импорт из Ticketmaster',
    'admin.name': 'Название', 'admin.artist': 'Артист', 'admin.location': 'Площадка и город', 'admin.datetime': 'Дата и время',
    'admin.rows': 'Ряды (A, B … AA)', 'admin.perRow': 'Мест в ряду', 'admin.priceA': 'Цена, ряд A', 'admin.priceStep': 'Шаг цены за ряд',
    'admin.create': 'Создать событие', 'admin.save': 'Сохранить', 'admin.cancel': 'Отмена', 'admin.edit': 'Изменить', 'admin.unpublish': 'Снять',
    'admin.confirmUnpublish': 'Снять с публикации это событие?', 'admin.noEvents': 'Нет событий для управления.', 'admin.loading': 'Загружаем события…',
    'admin.importTitle': 'Импорт из Ticketmaster', 'admin.keyword': 'Ключевое слово (необязательно)', 'admin.keywordPh': 'rock, jazz, Taylor Swift…',
    'admin.country': 'Код страны (необязательно)', 'admin.countryPh': 'DE', 'admin.city': 'Город (необязательно)', 'admin.cityPh': 'Berlin',
    'admin.category': 'Категория', 'admin.any': 'Любая', 'admin.order': 'Сортировка', 'admin.sortRandom': 'Случайный микс', 'admin.sortDate': 'Сначала ближайшие',
    'admin.size': 'Событий на страницу', 'admin.pages': 'Страниц', 'admin.importBtn': 'Импортировать', 'admin.importing': 'Импортируем события из Ticketmaster…',
    'admin.imported': 'Импортировано', 'admin.of': 'из', 'admin.skipped': 'уже было', 'admin.skippedNoDate': 'пропущено без подтверждённой даты',
    'admin.saving': 'Сохраняем…', 'admin.seatErr1': 'Укажите целое число рядов и мест в ряду',
    'admin.seatErr2': 'Максимум 500 мест (сейчас', 'admin.seatErr3': 'Цена должна быть больше 0, шаг цены — не меньше 0',
    'admin.seatsWord': 'мест', 'admin.rowsRange': 'ряды', 'admin.priceRange': 'цена',
    'admin.aiToday': 'AI сегодня', 'admin.aiTotal': 'запросов всего', 'admin.aiYou': 'вы', 'admin.aiCache': 'кэш', 'admin.aiFallback': 'fallback',
    'admin.aiErrors': 'ошибки', 'admin.aiNeurons': 'нейронов', 'admin.aiEmbed': 'эмбеддингов', 'admin.aiUnavailable': 'Статистика AI недоступна.',
    'admin.saveFail': 'Не удалось сохранить событие.', 'admin.loadFail': 'Не удалось загрузить события.'
  },
  en: {
    'doc.title': 'PulsePass — Live Events & Concert Tickets',
    'nav.events': 'Events', 'nav.how': 'How it works', 'nav.ai': 'AI picks', 'nav.faq': 'FAQ', 'nav.signin': 'Sign in',
    'hero.eyebrow': 'Live events',
    'hero.title': 'Find your next unforgettable night.',
    'hero.subtitle': 'Search concerts, pick your seat on an interactive map and book in a couple of clicks. No queues, no extra pages.',
    'search.artist': 'Artist', 'search.artistPh': 'Search artist', 'search.location': 'Location', 'search.locationPh': 'City or venue', 'search.date': 'Date', 'search.submit': 'Search',
    'stats.events': 'events listed', 'stats.cities': 'cities', 'stats.seats': 'seats available',
    'next.days': 'days', 'next.hours': 'hours', 'next.minutes': 'min', 'next.seconds': 'sec', 'next.choose': 'Choose seats',
    'events.title': 'Upcoming events',
    'filter.all': 'All', 'filter.soonest': 'Soonest first', 'filter.cheapest': 'Cheapest first', 'filter.weekend': 'This weekend',
    'card.buy': 'Choose seats', 'card.from': 'from', 'card.soldout': 'Sold out',
    'events.empty': 'No events yet.', 'events.noResults': 'Nothing found. Try changing your query.', 'events.loadError': 'Could not load events. Check API availability.',
    'how.title': 'How it works',
    'how.s1t': 'Find an event', 'how.s1d': 'Search by artist, city and date — or describe your wishes to the AI assistant.',
    'how.s2t': 'Pick your seat', 'how.s2d': 'The seat map shows free and taken seats in real time. Grab several tickets at once.',
    'how.s3t': 'Pay and go', 'how.s3d': 'Your ticket lands in your account — and if plans change, return it in a couple of clicks.',
    'ai.title': 'AI picks',
    'ai.hint': 'Describe what you want: “a concert in Berlin” or “something jazz” — the assistant will suggest events with prices.',
    'ai.placeholder': 'e.g. a rock concert in Berlin', 'ai.ask': 'Ask',
    'ai.needAuth': 'Sign in to use the AI assistant.', 'ai.error': 'The assistant is unavailable right now.', 'ai.from': 'from',
    'ai.ex1': 'I want a concert in Berlin', 'ai.ex2': 'Something jazz', 'ai.ex3': 'A rock concert this weekend',
    'faq.title': 'Frequently asked questions',
    'q1': 'Do I need an account to buy a ticket?', 'a1': 'Yes — that is how we keep your tickets and purchase history. Registration takes under a minute.',
    'q2': 'Can I return a ticket?', 'a2': 'Yes. Every ticket in your account has a “Return ticket” button — the seat becomes available again and the order is cancelled.',
    'q3': 'How do I know which seats are taken?', 'a3': 'The seat map syncs with the server: white seats are free, gray ones are taken or being paid for.',
    'q4': 'What can the AI assistant do?', 'a4': 'Describe your request in plain words — city, genre, mood — and it will suggest matching events with prices.',
    'q5': 'Is this a real payment service?', 'a5': 'No: PulsePass is a demo project. Payments are simulated, card data is never stored or sent anywhere.',
    'cta.title': 'Your night starts here', 'cta.text': 'Create an account — tickets, history and AI picks always at hand.',
    'cta.signup': 'Create account', 'cta.browse': 'Browse events',
    'footer.tagline': 'Live experiences and simple ticketing.', 'footer.explore': 'Explore',
    'footer.rights': '© {year} PulsePass — demo project. Tickets and payments are simulated.',
    'common.close': 'Close',
    'auth.showPw': 'Show password',
    'auth.hidePw': 'Hide password',
    'events.upcoming': 'Coming up',
    'toast.seatTaken': 'Seat {seat} was just taken',
    'stub.title': 'This is a stub', 'stub.text': 'This button is not real: the feature is not wired up in the demo.',
    'auth.signin': 'Sign in', 'auth.register': 'Register', 'auth.fullName': 'Full name', 'auth.fullNamePh': 'John Smith',
    'auth.email': 'Email', 'auth.emailPh': 'you@example.com', 'auth.password': 'Password', 'auth.passwordPh': '••••••••', 'auth.confirm': 'Confirm password',
    'auth.forgot': 'Forgot password?', 'auth.or': 'or continue with', 'auth.yandex': 'Yandex ID', 'auth.sber': 'Sber ID',
    'auth.submitLogin': 'Sign in', 'auth.submitRegister': 'Create account', 'auth.creating': 'Creating account…',
    'auth.welcome': 'Welcome back!', 'auth.registered': 'Registration complete. You can buy tickets now.',
    'auth.authFailed': 'Authentication failed.', 'auth.mismatch': 'Passwords do not match', 'auth.enterEmail': 'Enter a valid email',
    'auth.enterName': 'Enter your name (at least 2 characters)', 'auth.shortPw': 'Password must be at least 6 characters', 'auth.needPurchase': 'Please sign in before purchasing tickets.',
    'auth.sideTitle': 'Your pass to the best nights', 'auth.sideText': 'One account — tickets, returns and AI picks.',
    'auth.f1': 'Tickets always at hand', 'auth.f2': 'One-click returns', 'auth.f3': 'AI event picks',
    'auth.pwHint': 'password strength', 'auth.pw0': 'weak', 'auth.pw1': 'fair', 'auth.pw2': 'good', 'auth.pw3': 'strong',
    'pay.title': 'Payment', 'pay.name': 'Cardholder name', 'pay.namePh': 'JOHN SMITH', 'pay.number': 'Card number',
    'pay.expiry': 'Expiry', 'pay.cvc': 'CVC', 'pay.ticket': 'Selected tickets', 'pay.pay': 'Pay now', 'pay.processing': 'Processing…',
    'event.selectSeat': 'Select your seats', 'event.noSeat': 'No seat selected', 'event.stage': 'Stage', 'event.close': 'Close',
    'event.buy': 'Buy ticket', 'event.buyN': 'Buy tickets', 'event.source': 'Event details on Ticketmaster ↗',
    'legend.free': 'Available', 'legend.selected': 'Selected', 'legend.booked': 'Taken / paying',
    'dash.back': '← Back to site', 'dash.hello': 'My account', 'dash.logout': 'Log out', 'dash.since': 'member since',
    'dash.tickets': 'My tickets', 'dash.upcoming': 'Upcoming', 'dash.past': 'Past',
    'dash.statTickets': 'tickets total', 'dash.statUpcoming': 'upcoming', 'dash.statSpent': 'spent',
    'dash.empty': 'No orders yet. Tickets will appear here after purchase.', 'dash.emptyPast': 'No past events yet.',
    'dash.seatRow': 'Row', 'dash.seatNo': 'seat', 'dash.return': 'Return ticket',
    'dash.notFoundT': 'Event not found', 'dash.notFoundD': 'Could not find the event for the return.',
    'dash.findEvents': 'Find events', 'dash.retry': 'Retry',
    'dash.profileErr': 'Could not load the profile.', 'dash.ordersErr': 'Could not load your tickets.',
    'dash.confirmTitle': 'Return this ticket?', 'dash.confirmCancel': 'Cancel',
    'dash.changePhoto': 'Change photo', 'dash.removePhoto': 'Remove photo', 'dash.avatarAlt': 'Profile photo',
    'dash.adminBadge': 'Administrator', 'dash.order': 'Order #',
    'dash.loggedOut': 'You are signed out',
    'dash.avatarBadType': 'Invalid file type', 'dash.avatarBadTypeD': 'PNG, JPEG and WebP are supported.',
    'dash.avatarTooLarge': 'File is too large', 'dash.avatarTooLargeD': 'Maximum size is 5 MB.',
    'dash.avatarFail': 'Could not save the photo', 'dash.avatarFailD': 'Try another image.',
    'dash.avatarSaved': 'Profile photo updated', 'dash.avatarSavedD': 'Stored in this browser (demo).',
    'toast.payOkT': 'Payment completed', 'toast.payOkD': 'Ticket confirmed.',
    'toast.payFailT': 'Payment failed', 'toast.payFailD': 'Check the card details and try again.',
    'toast.retOkT': 'Ticket returned', 'toast.retOkD': 'the seat is available again.',
    'toast.retFailT': 'Return failed', 'toast.retFailD': 'Could not return the ticket right now.',
    'toast.yourSeat': 'Your ticket', 'toast.booked': 'Booked', 'toast.payingSeat': 'Payment in progress',
    'admin.manage': 'Manage events', 'admin.new': 'New event', 'admin.import': 'Import from Ticketmaster',
    'admin.name': 'Event name', 'admin.artist': 'Artist', 'admin.location': 'Venue and location', 'admin.datetime': 'Date and time',
    'admin.rows': 'Rows (A, B … AA)', 'admin.perRow': 'Seats per row', 'admin.priceA': 'Price, row A', 'admin.priceStep': 'Price step per row',
    'admin.create': 'Create event', 'admin.save': 'Save', 'admin.cancel': 'Cancel', 'admin.edit': 'Edit', 'admin.unpublish': 'Unpublish',
    'admin.confirmUnpublish': 'Unpublish this event?', 'admin.noEvents': 'No events to manage.', 'admin.loading': 'Loading events…',
    'admin.importTitle': 'Import from Ticketmaster', 'admin.keyword': 'Keyword (optional)', 'admin.keywordPh': 'rock, jazz, Taylor Swift…',
    'admin.country': 'Country code (optional)', 'admin.countryPh': 'DE', 'admin.city': 'City (optional)', 'admin.cityPh': 'Berlin',
    'admin.category': 'Category', 'admin.any': 'Any', 'admin.order': 'Order', 'admin.sortRandom': 'Random mix', 'admin.sortDate': 'Soonest first',
    'admin.size': 'Events per page', 'admin.pages': 'Pages', 'admin.importBtn': 'Import events', 'admin.importing': 'Importing events from Ticketmaster…',
    'admin.imported': 'Imported', 'admin.of': 'of', 'admin.skipped': 'already existed', 'admin.skippedNoDate': 'skipped without confirmed date',
    'admin.saving': 'Saving…', 'admin.seatErr1': 'Enter a whole number of rows and seats per row',
    'admin.seatErr2': 'Maximum 500 seats (now', 'admin.seatErr3': 'Price must be above 0, price step — not below 0',
    'admin.seatsWord': 'seats', 'admin.rowsRange': 'rows', 'admin.priceRange': 'price',
    'admin.aiToday': 'AI today', 'admin.aiTotal': 'requests total', 'admin.aiYou': 'you', 'admin.aiCache': 'cache', 'admin.aiFallback': 'fallback',
    'admin.aiErrors': 'errors', 'admin.aiNeurons': 'neurons', 'admin.aiEmbed': 'embeddings', 'admin.aiUnavailable': 'AI stats unavailable.',
    'admin.saveFail': 'Could not save the event.', 'admin.loadFail': 'Could not load events.'
  }
};
const t = (key) => I18N[currentLang][key] ?? key;
const localeTag = () => (currentLang === 'ru' ? 'ru-RU' : 'en-US');
function ruPlural(n, forms) {
  const n10 = n % 10, n100 = n % 100;
  if (n10 === 1 && n100 !== 11) return forms[0];
  if (n10 >= 2 && n10 <= 4 && (n100 < 10 || n100 >= 20)) return forms[1];
  return forms[2];
}
function seatsLeftText(n) {
  if (currentLang === 'ru') return `Осталось ${n} ${ruPlural(n, ['место', 'места', 'мест'])}`;
  return n === 1 ? '1 seat left' : `${n} seats left`;
}
function eventsCountText(n) {
  if (currentLang === 'ru') return `${n} ${ruPlural(n, ['событие', 'события', 'событий'])}`;
  return `${n} ${n === 1 ? 'event' : 'events'}`;
}

/* ================= i18n: apply language ================= */
let appInitialized = false;
let langSwitching = false;

function applyLanguage(lang) {
  if (appInitialized && lang !== currentLang) { switchLanguage(lang); return; }
  applyLanguageNow(lang);
}

function applyLanguageNow(lang) {
  currentLang = lang;
  localStorage.setItem('pulsepass-lang', lang);
  document.documentElement.lang = lang;
  document.title = t('doc.title');
  const year = String(Math.max(2026, new Date().getFullYear()));
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const value = I18N[lang][el.dataset.i18n];
    if (value != null) el.innerHTML = value.replace('{year}', year);
  });
  document.querySelectorAll('[data-i18n-ph]').forEach((el) => {
    el.placeholder = I18N[lang][el.dataset.i18nPh] || '';
  });
  document.querySelectorAll('[data-i18n-aria]').forEach((el) => {
    const value = I18N[lang][el.dataset.i18nAria];
    if (value != null) el.setAttribute('aria-label', value.replace('{year}', year));
  });
  syncPwToggleLabels();

  $('langToggle')?.classList.toggle('en-active', lang === 'en');

  $('langRu')?.classList.toggle('active', lang === 'ru');
  $('langEn')?.classList.toggle('active', lang === 'en');
  buildMarquee();
  if (allEvents.length) refreshView(lastEmptyKey);
  if (dashProfileLoaded) renderProfileCard();
  if (dashOrdersLoaded) renderOrders(dashboardOrders);
  if (currentUser?.role === 'ADMIN' && adminEvents.length) renderAdminEvents(adminEvents);
  updateSeatSummary();
  updateSeatLegend();
  updateBuyButton();
  updateStrength();
  updateAuthButton();
  if (selectedEvent) $('selectedSeatLabel').textContent = selectedLabelFor(selectedEvent);
  const submit = $('authSubmitBtn');
  if (submit) submit.textContent = authMode === 'register' ? t('auth.submitRegister') : t('auth.submitLogin');
}

function switchLanguage(lang) {
  if (lang === currentLang || langSwitching) return;
  langSwitching = true;
  const finish = () => { langSwitching = false; };
  if (document.startViewTransition) {
    document.startViewTransition(() => applyLanguageNow(lang)).finished.finally(finish);
  } else {
    document.body.classList.add('lang-fading');
    setTimeout(() => {
      applyLanguageNow(lang);
      requestAnimationFrame(() => {
        document.body.classList.remove('lang-fading');
        setTimeout(finish, 160);
      });
    }, 140);
  }
}

/* ================= helpers ================= */
const $ = (id) => document.getElementById(id);
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}
function fmtDateBadge(d) {
  return new Date(d).toLocaleDateString(localeTag(), { month: 'short', day: 'numeric' }).toUpperCase();
}
function formatLongDate(d) {
  return new Date(d).toLocaleString(localeTag(), { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}
function fmtEventWhen(d) {
  const date = new Date(d);
  return `${date.toLocaleDateString(localeTag(), { weekday: 'short', day: 'numeric', month: 'short' })} · ${date.toLocaleTimeString(localeTag(), { hour: 'numeric', minute: '2-digit' })}`;
}
const getStartingPrice = (seats = []) => seats.length ? Math.min(...seats.map((s) => Number(s.price || 0))) : 0;
const getCityLabel = (location = '') => String(location || '').split(',').slice(-1)[0].trim();
function splitLocation(location) {
  const parts = String(location || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!parts.length) return { venue: '', city: '' };
  if (parts.length === 1) return { venue: parts[0], city: parts[0] };
  return { venue: parts.slice(0, -1).join(', '), city: parts[parts.length - 1] };
}
function coverClass(event) {
  const index = Math.max(allEvents.findIndex((e) => String(e.id) === String(event.id)), 0);
  return `cover-${(index % 4) + 1}`;
}
const getSeatKey = (row, seatNumber) => `${String(row || '').trim().toUpperCase()}${Number(seatNumber)}`;
function initialsFor(name) {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  const first = parts[0].charAt(0) || '';
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
  return (first + last).toUpperCase();
}
function firstNameOf(name) {
  return String(name || '').trim().split(/\s+/)[0] || String(name || '');
}

/* ================= v17: дедупликация событий ================= */
// Одинаковые события (повторный импорт Ticketmaster, двойной сид и т.д.) дают
// разные id, но совпадают по подписи. Схлопываем в одну запись — берём первую.
function dedupeEvents(events) {
  if (!Array.isArray(events)) return [];
  const seen = new Set();
  return events.filter((e) => {
    if (!e) return false;
    const sig = [
      String(e.name || '').toLowerCase().trim(),
      String(e.eventDate || ''),
      String(e.location || '').toLowerCase().trim(),
      String(e.artist || '').toLowerCase().trim()
    ].join('|');
    if (seen.has(sig)) return false;
    seen.add(sig);
    return true;
  });
}

/* ================= notifications (тосты) ================= */
let toastHost = null;

function getToastHost() {
  if (!toastHost || !document.body.contains(toastHost)) {
    toastHost = document.createElement('div');
    toastHost.className = 'toast-host';
    document.body.appendChild(toastHost);
  }
  return toastHost;
}

function dismissToast(el) {
  if (!el || el.dataset.leaving === '1') return;
  el.dataset.leaving = '1';
  el.classList.add('toast-out');
  setTimeout(() => el.remove(), 240);
}

function notify(kind, titleText, detailText) {
  const defaults = {
    success: [t('toast.payOkT'), t('toast.payOkD')],
    error: [t('toast.payFailT'), t('toast.payFailD')],
    info: [t('stub.title'), t('stub.text')]
  };
  const [defTitle, defDetail] = defaults[kind] || defaults.info;
  const icons = {
    success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.4 2.4 4.6-5.4"/></svg>',
    error: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7.5V13"/><path d="M12 16.4v.01"/></svg>',
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8v.01"/></svg>'
  };
  const host = getToastHost();
  const active = [...host.children].filter((child) => child.dataset.leaving !== '1');
  while (active.length >= 3) dismissToast(active.shift());
  const el = document.createElement('div');
  el.className = `toast toast--${kind}`;
  el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  el.innerHTML = `
    <span class="toast-icon" aria-hidden="true">${icons[kind] || icons.info}</span>
    <span class="toast-content">
      <strong class="toast-title">${escapeHtml(titleText ?? defTitle)}</strong>
      ${detailText ? `<span class="toast-text">${escapeHtml(detailText)}</span>` : ''}
    </span>
    <button type="button" class="toast-close" aria-label="${escapeHtml(t('common.close'))}">×</button>
    <span class="toast-progress" aria-hidden="true"></span>`;
  host.appendChild(el);

  let remaining = 5000;
  let expiresAt = 0;
  let timerId = null;
  const dismiss = () => { if (timerId) clearTimeout(timerId); dismissToast(el); };
  const startTimer = () => {
    expiresAt = Date.now() + remaining;
    el.style.setProperty('--toast-life', `${remaining}ms`);
    const bar = el.querySelector('.toast-progress');
    if (bar) { bar.style.animation = 'none'; void bar.offsetWidth; bar.style.animation = ''; }
    el.classList.remove('is-paused');
    timerId = setTimeout(dismiss, remaining);
  };
  const pauseTimer = () => {
    if (!timerId) return;
    remaining = Math.max(0, expiresAt - Date.now());
    clearTimeout(timerId);
    timerId = null;
    el.classList.add('is-paused');
  };
  el.querySelector('.toast-close').addEventListener('click', dismiss);
  el.addEventListener('mouseenter', pauseTimer);
  el.addEventListener('mouseleave', startTimer);
  let touchStartX = null;
  el.addEventListener('touchstart', (e) => { touchStartX = e.touches[0].clientX; }, { passive: true });
  el.addEventListener('touchend', (e) => {
    if (touchStartX !== null && e.changedTouches[0].clientX - touchStartX > 60) dismiss();
  }, { passive: true });
  startTimer();
}

function hideNotify(el) { if (el) dismissToast(el); }

/* ================= seats ================= */
const isSeatBeingPaid = (eventId, row, seatNumber) => seatsBeingPaid.get(String(eventId))?.has(getSeatKey(row, seatNumber)) || false;
function getBackendSeatStatus(seat) {
  if (!seat) return 'AVAILABLE';
  if (seat.booked === true || seat.available === false) return 'BOOKED';
  const status = String(seat.status || seat.bookingStatus || seat.state || '').toUpperCase();
  if (['BOOKED', 'PAID', 'RESERVED', 'SOLD'].includes(status)) return 'BOOKED';
  if (['PENDING', 'PAYMENT', 'PAYING', 'PROCESSING', 'HOLD', 'HELD'].includes(status)) return 'PAYING';
  return 'AVAILABLE';
}
async function hydrateSeatStatusesForEvents(events) {
  if (!Array.isArray(events)) return events;
  await Promise.all(events.map(async (event) => {
    if (!event?.id) return;
    try {
      const response = await fetch(`${BOOKING_API_BASE}/api/bookings/events/${event.id}/seat-status`);
      if (!response.ok) return;
      const statuses = await response.json();
      const lookup = new Map((statuses || []).map((s) => [`${s.row}-${s.seatNumber}`, s.status]));
      (event.seats || []).forEach((seat) => {
        const backendStatus = lookup.get(`${seat.row}-${seat.seatNumber}`);
        if (!backendStatus) return;
        seat.status = backendStatus;
        seat.available = backendStatus === 'AVAILABLE';
        seat.booked = ['RESERVED', 'SOLD'].includes(backendStatus);
      });
    } catch (error) { console.warn('Seat status sync failed', event.id, error); }
  }));
  return events;
}
function compareRows(a, b) { return a.length - b.length || a.localeCompare(b); }
function getSeatMap(event) {
  const seats = event.seats || [];
  const seatLookup = new Map(seats.map((seat) => [`${seat.row}-${seat.seatNumber}`, seat]));
  const rows = [...new Set(seats.map((seat) => seat.row))].sort(compareRows);
  const maxSeatNumber = Math.max(0, ...seats.map((seat) => Number(seat.seatNumber)));
  const seatNumbers = Array.from({ length: maxSeatNumber }, (_, i) => i + 1);
  return rows.map((row) => ({
    row,
    seats: seatNumbers.map((seatNumber) => {
      const seat = seatLookup.get(`${row}-${seatNumber}`);
      let status = seat ? getBackendSeatStatus(seat) : 'EMPTY';
      if (seat && isSeatBeingPaid(event.id, row, seatNumber)) status = 'PAYING';
      return {
        row, seatNumber, status,
        available: status === 'AVAILABLE', booked: status === 'BOOKED', paying: status === 'PAYING',
        price: seat ? Number(seat.price || 0) : 0, isSeat: Boolean(seat)
      };
    })
  }));
}

/* ================= rendering: landing ================= */
function renderSkeletons() {
  const grid = $('eventGrid');
  if (!grid) return;
  grid.innerHTML = Array.from({ length: 8 }, () => `
    <article class="event-card" aria-hidden="true">
      <div class="skeleton s-img"></div>
      <div class="event-body">
        <div class="skeleton s-line"></div><div class="skeleton s-line short"></div>
        <div class="skeleton s-line"></div><div class="skeleton s-line short"></div>
      </div>
    </article>`).join('');
}
function renderEvents(events, emptyKey = 'events.empty') {
  const grid = $('eventGrid');
  if (!grid) return;
  if (!events.length) { grid.innerHTML = `<p class="empty-state">${escapeHtml(t(emptyKey))}</p>`; return; }
  grid.innerHTML = events.map((event, index) => {
    const free = (event.seats || []).filter((s) => getBackendSeatStatus(s) === 'AVAILABLE').length;
    const badge = free === 0
        ? `<span class="seats-badge out">${escapeHtml(t('card.soldout'))}</span>`
        : `<span class="seats-badge ${free <= 10 ? 'few' : ''}">${escapeHtml(seatsLeftText(free))}</span>`;
    return `
    <article class="event-card card-enter" style="--card-i:${index}" data-open-event="${escapeHtml(event.id)}">
      <div class="event-image">
        <div class="event-media ${coverClass(event)}"${event.imageUrl ? ` style="background-image:url('${escapeHtml(event.imageUrl)}')"` : ''}></div>
        ${badge}
      </div>
      <div class="event-body">
        <div class="event-meta-row">
          <span class="date-badge">${escapeHtml(fmtDateBadge(event.eventDate))}</span>
          <span class="location">${escapeHtml(getCityLabel(event.location))}</span>
        </div>
        <h3>${escapeHtml(event.name)}</h3>
        <p>${escapeHtml(event.location)}</p>
        <div class="event-footer">
          <span class="tag">${escapeHtml(event.artist || 'Live')}</span>
          <strong>${escapeHtml(t('card.from'))} $${getStartingPrice(event.seats)}</strong>
        </div>
        <button class="card-buy-btn" type="button">${escapeHtml(t('card.buy'))}</button>
      </div>
    </article>`;
  }).join('');
}
function paginationItems(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const items = [1];
  const start = Math.max(2, current - 2), end = Math.min(total - 1, current + 2);
  if (start > 2) items.push('…');
  for (let page = start; page <= end; page++) items.push(page);
  if (end < total - 1) items.push('…');
  items.push(total);
  return items;
}
function renderPagination(totalPages) {
  const nav = $('eventPagination');
  if (!nav) return;
  if (!allEvents.length || totalPages <= 1) { nav.innerHTML = ''; nav.classList.add('hidden'); return; }
  nav.classList.remove('hidden');
  nav.innerHTML = `
    <button type="button" data-page="${currentPage - 1}" ${currentPage === 1 ? 'disabled' : ''} aria-label="Previous page">←</button>
    ${paginationItems(currentPage, totalPages).map((item) => item === '…'
      ? '<span class="pagination-ellipsis">…</span>'
      : `<button type="button" data-page="${item}" class="${item === currentPage ? 'active' : ''}" ${item === currentPage ? 'aria-current="page"' : ''}>${item}</button>`).join('')}
    <button type="button" data-page="${currentPage + 1}" ${currentPage === totalPages ? 'disabled' : ''} aria-label="Next page">→</button>`;
}
const sortedByDate = (events) => [...events].sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate));
function nextWeekendRange() {
  const now = new Date();
  const day = now.getDay();
  const diffToFriday = (day <= 5 ? 5 - day : 7 - day + 5);
  const friday = new Date(now); friday.setDate(now.getDate() + diffToFriday); friday.setHours(0, 0, 0, 0);
  const sunday = new Date(friday); sunday.setDate(friday.getDate() + 2); sunday.setHours(23, 59, 59, 999);
  return [friday, sunday];
}
function filteredEvents() {
  let list = [...allEvents];
  if (activeFilter === 'soonest') list = sortedByDate(list);
  if (activeFilter === 'cheapest') list.sort((a, b) => getStartingPrice(a.seats) - getStartingPrice(b.seats));
  if (activeFilter === 'weekend') {
    const [fr, su] = nextWeekendRange();
    list = list.filter((e) => { const d = new Date(e.eventDate); return d >= fr && d <= su; });
  }
  return list;
}
function updateChips() {
  document.querySelectorAll('#filterChips .chip').forEach((c) => c.classList.toggle('active', c.dataset.filter === activeFilter));
}
function renderStats() {
  statTargets.events = allEvents.length;
  statTargets.cities = new Set(allEvents.map((e) => getCityLabel(e.location))).size;
  statTargets.seats = allEvents.reduce((sum, e) => sum + (e.seats || []).filter((s) => getBackendSeatStatus(s) === 'AVAILABLE').length, 0);
  if (statsAnimated) writeStats();
  else if (statsVisible) animateStats();
}
function setStatValue(id, value) {
  statAnimTokens[id] = (statAnimTokens[id] || 0) + 1;
  const el = $(id);
  if (el) el.textContent = value;
}
function writeStats() {
  setStatValue('statEvents', statTargets.events);
  setStatValue('statCities', statTargets.cities);
  setStatValue('statSeats', statTargets.seats);
}
function animateStats() {
  if (statsAnimated) return;
  statsAnimated = true;
  animateCountUp($('statEvents'), statTargets.events);
  animateCountUp($('statCities'), statTargets.cities);
  animateCountUp($('statSeats'), statTargets.seats);
}
function animateCountUp(el, target) {
  if (!el) return;
  const key = el.id;
  const token = (statAnimTokens[key] || 0) + 1;
  statAnimTokens[key] = token;
  const start = performance.now(), dur = 1000;
  const step = (now) => {
    if (statAnimTokens[key] !== token) return;
    const p = Math.min((now - start) / dur, 1);
    el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
    if (p < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}
function rollValue(el, value) {
  if (!el || el.dataset.v === value) return;
  const previous = el.dataset.v;
  el.dataset.v = value;
  if (previous == null) { el.textContent = value; return; }
  el.innerHTML = `<span class="cd-old" aria-hidden="true">${previous}</span><span class="cd-new">${value}</span>`;
}
function renderCountdownBlock() {
  const section = $('spotlightSection');
  if (!section) return;
  const dated = allEvents
      .filter((e) => e.eventDate && Number.isFinite(new Date(e.eventDate).getTime()) && new Date(e.eventDate).getTime() > Date.now())
      .sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate));
  if (!dated.length) { section.classList.add('hidden'); clearInterval(countdownTimer); return; }
  const next = dated[0];
  $('spotName').textContent = next.name;
  $('spotMeta').textContent = `${next.artist || ''} · ${next.location} · ${formatLongDate(next.eventDate)}`;
  $('spotCta').dataset.openEvent = String(next.id);
  section.classList.remove('hidden');
  clearInterval(countdownTimer);
  countdownTarget = new Date(next.eventDate).getTime();
  const tick = () => {
    const diff = Math.max(0, countdownTarget - Date.now());
    const pad = (n) => String(n).padStart(2, '0');
    rollValue($('cdDays'), pad(Math.floor(diff / 86400000)));
    rollValue($('cdHours'), pad(Math.floor(diff / 3600000) % 24));
    rollValue($('cdMins'), pad(Math.floor(diff / 60000) % 60));
    rollValue($('cdSecs'), pad(Math.floor(diff / 1000) % 60));
  };
  tick();
  countdownTimer = setInterval(tick, 1000);
}

function closeAdminEventForm() {
  editingEventId = null;
  $('adminEventForm')?.classList.add('hidden');
  setSeatConfigVisible(true);
  showAdminStatus('');
}

function renderUpcoming() {
  const section = $('upcomingSection'), list = $('upcoming');
    if (!section || !list) return;
    const items = sortedByDate(allEvents).slice(1, 4);
    if (!items.length) { section.classList.add('hidden'); return; }
    list.innerHTML = items.map((event) => `
    <article class="small-card" data-open-event="${escapeHtml(event.id)}">
      <div class="small-thumb ${coverClass(event)}"${event.imageUrl ? ` style="background-image:url('${escapeHtml(event.imageUrl)}')"` : ''}></div>
      <div><span>${escapeHtml(fmtEventWhen(event.eventDate))}</span>
        <h3>${escapeHtml(event.name)}</h3><p>${escapeHtml(event.location)}</p></div>
    </article>`).join('');
    section.classList.remove('hidden');
  }
  function buildMarquee() {
    const box = $('marqueeBox');
    const track = $('marqueeTrack');
    if (!box || !track) return;
    const counts = new Map();
    allEvents.forEach((event) => {
      const city = getCityLabel(event.location);
      if (city) counts.set(city, (counts.get(city) || 0) + 1);
    });
    const entries = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 9);
    if (!entries.length) { box.classList.add('hidden'); track.innerHTML = ''; return; }
    box.classList.remove('hidden');
    const sep = '<span class="mq-sep" aria-hidden="true"></span>';
    const items = entries.map(([city, n]) =>
        `<span class="mq-item"><span class="mq-city">${escapeHtml(city)}</span><span class="mq-count">· ${escapeHtml(eventsCountText(n))}</span></span>`
    ).join(sep);
    track.innerHTML = `<span class="mq-block">${items}</span>`;
    const block = track.firstElementChild;
    if (!block) return;
    const blockWidth = block.offsetWidth;
    if (!blockWidth) return;
    const viewport = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0);
    const copies = Math.max(2, Math.ceil((viewport + blockWidth) / blockWidth));
    const fragment = document.createDocumentFragment();
    for (let i = 1; i < copies; i++) fragment.appendChild(block.cloneNode(true));
    track.appendChild(fragment);
    const speed = viewport < 720 ? 26 : 40;
    track.style.setProperty('--marquee-shift', `${-blockWidth}px`);
    track.style.setProperty('--marquee-duration', `${(blockWidth / speed).toFixed(2)}s`);
  }
  function refreshView(emptyKey = 'events.empty') {
    lastEmptyKey = emptyKey;
    const list = filteredEvents();
    const totalPages = Math.max(1, Math.ceil(list.length / EVENTS_PER_PAGE));
    currentPage = Math.min(Math.max(currentPage, 1), totalPages);
    const start = (currentPage - 1) * EVENTS_PER_PAGE;
    renderEvents(list.slice(start, start + EVENTS_PER_PAGE), emptyKey);
    renderPagination(totalPages);
    renderStats();
    renderCountdownBlock();
    renderUpcoming();
    buildMarquee();
  }

  /* ================= modals ================= */
  const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
  const modalCloseTimers = {};
  let lastFocusedElement = null;

  function openModal(name) {
    const modal = $(`${name}Modal`);
    if (!modal) return;
    if (modalCloseTimers[name]) { clearTimeout(modalCloseTimers[name]); delete modalCloseTimers[name]; }
    modal.classList.remove('closing');
    modal.classList.remove('hidden');
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    lastFocusedElement = document.activeElement;
    const panel = modal.querySelector('.modal-panel');
    if (panel) requestAnimationFrame(() => {
      const visible = (el) => el.offsetParent !== null && window.getComputedStyle(el).visibility !== 'hidden';
      const items = Array.from(panel.querySelectorAll(FOCUSABLE_SELECTOR)).filter(visible);
      const target = items.find((el) => ['INPUT', 'SELECT', 'TEXTAREA'].includes(el.tagName)) || items[0];
      if (target) target.focus();
    });
  }

  function closeModal(name) {
    const modal = $(`${name}Modal`);
    if (!modal || modal.classList.contains('hidden') || modal.classList.contains('closing')) return;
    if (name === 'event') stopSeatPolling();
    modal.classList.add('closing');
    modalCloseTimers[name] = setTimeout(() => {
      delete modalCloseTimers[name];
      modal.classList.remove('closing');
      modal.classList.add('hidden');
      modal.setAttribute('aria-hidden', 'true');
      if (!document.querySelector('.modal:not(.hidden)')) document.body.classList.remove('modal-open');
      if (lastFocusedElement && document.contains(lastFocusedElement) && lastFocusedElement.offsetParent !== null) {
        lastFocusedElement.focus();
      }
    }, 190);
  }

  /* ================= event modal ================= */
  function updateBuyButton() {
    const btn = $('buyTicketBtn');
    if (!btn) return;
    const count = selectedEvent
        ? selectedSeats.filter((s) => String(s.eventId) === String(selectedEvent.id)).length
        : 0;
    btn.disabled = count === 0;
    btn.textContent = count > 0 ? `${t('event.buyN')} (${count})` : t('event.buy');
  }
  function selectedLabelFor(event) {
    const names = selectedSeats.filter((s) => s.eventId === event.id).map((s) => `${s.row}${s.seatNumber}`);
    return names.length ? names.join(', ') : t('event.noSeat');
  }
  function updateSeatLegend() {
    const legend = document.querySelector('.seat-legend');
    if (!legend) return;
    legend.innerHTML = `
    <div class="seat-legend-item"><span class="seat-legend-color free"></span><span>${escapeHtml(t('legend.free'))}</span></div>
    <div class="seat-legend-item"><span class="seat-legend-color selected"></span><span>${escapeHtml(t('legend.selected'))}</span></div>
    <div class="seat-legend-item"><span class="seat-legend-color booked"></span><span>${escapeHtml(t('legend.booked'))}</span></div>`;
  }
  let seatAnimTimer = null;

  function openEventModal(event) {
    selectedEvent = event;
    selectedSeats = selectedSeats.filter((s) => String(s.eventId) === String(event.id));
    const seatMap = $('seatMap');
    seatMap.innerHTML = '';
    const thumb = $('modalThumb');
    if (thumb) {
      thumb.className = `modal-event-thumb ${coverClass(event)}`;
      thumb.style.backgroundImage = event.imageUrl ? `url("${event.imageUrl}")` : '';
    }
    $('modalTitle').textContent = event.name;
    $('modalMeta').textContent = `${event.location} • ${formatLongDate(event.eventDate)}`;
    $('modalDescription').textContent = event.description
        || `${event.artist ? `${event.artist} · ` : ''}${event.location}`;
    $('modalTag').textContent = event.artist || 'Live';
    const source = $('modalSource');
    if (source) { source.href = event.sourceUrl || ''; source.classList.toggle('hidden', !event.sourceUrl); }
    $('selectedSeatLabel').textContent = selectedLabelFor(event);
    updateBuyButton();
    const owned = ownedSeats.get(String(event.id)) || new Set();
    const rows = getSeatMap(event);
    rows.forEach((row, rowIndex) => {
      const rowEl = document.createElement('div');
      rowEl.className = 'seat-row';
      rowEl.style.setProperty('--row-i', String(rowIndex));
      const rowLabel = document.createElement('span');
      rowLabel.className = 'seat-row-label';
      rowLabel.textContent = row.row;
      rowEl.appendChild(rowLabel);
      row.seats.forEach((seat) => {
        if (!seat.isSeat) { const empty = document.createElement('div'); empty.className = 'seat-empty'; rowEl.appendChild(empty); return; }
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = String(seat.seatNumber);
        button.dataset.row = seat.row;
        button.dataset.seatNumber = String(seat.seatNumber);
        const label = `${seat.row}${seat.seatNumber}`;
        const isOwned = owned.has(getSeatKey(seat.row, seat.seatNumber));
        if (seat.booked || isOwned) {
          button.className = 'seat-btn booked'; button.disabled = true;
          button.title = `${label} • ${isOwned ? t('toast.yourSeat') : t('toast.booked')}`;
          rowEl.appendChild(button); return;
        }
        if (seat.paying) {
          button.className = 'seat-btn paying'; button.disabled = true;
          button.title = `${label} • ${t('toast.payingSeat')}`;
          rowEl.appendChild(button); return;
        }
        button.className = 'seat-btn available';
        button.title = `${label} • $${seat.price}`;
        if (selectedSeats.some((s) => s.eventId === event.id && s.row === seat.row && s.seatNumber === seat.seatNumber)) button.classList.add('selected');
        button.addEventListener('click', () => {
          const same = (s) => s.eventId === event.id && s.row === seat.row && s.seatNumber === seat.seatNumber;
          if (selectedSeats.some(same)) selectedSeats = selectedSeats.filter((s) => !same(s));
          else selectedSeats.push({ eventId: event.id, row: seat.row, seatNumber: seat.seatNumber, price: Number(seat.price || 0), label });
          button.classList.remove('pop');
          void button.offsetWidth;
          button.classList.add('pop');
          $('selectedSeatLabel').textContent = selectedLabelFor(event);
          seatMap.querySelectorAll('.seat-btn').forEach((btn) => {
            const selected = selectedSeats.some((s) => s.eventId === event.id && s.row === btn.dataset.row && s.seatNumber === Number(btn.dataset.seatNumber));
            btn.classList.toggle('selected', selected);
          });
          updateBuyButton();
        });
        rowEl.appendChild(button);
      });
      seatMap.appendChild(rowEl);
    });
    seatMap.classList.add('animate-rows');
    clearTimeout(seatAnimTimer);
    seatAnimTimer = setTimeout(() => seatMap.classList.remove('animate-rows'), rows.length * 22 + 420);
    document.querySelector('.seat-legend')?.remove();
    const legend = document.createElement('div');
    legend.className = 'seat-legend';
    seatMap.parentElement.appendChild(legend);
    updateSeatLegend();
    openModal('event');
    startSeatPolling();
  }

  /* ================= session / JWT ================= */
  function readCurrentUser() {
    try { const raw = localStorage.getItem('pulsepass-user'); return raw ? JSON.parse(raw) : null; }
    catch (error) { console.warn('Failed to read user session:', error); return null; }
  }
  function decodeJwtPayload(token) {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')));
    } catch (error) { return null; }
  }
  function isAccessTokenExpired(token) {
    const payload = decodeJwtPayload(token);
    if (!payload || !payload.exp) return false;
    return Date.now() >= payload.exp * 1000 - 30000;
  }
  async function ensureValidAccessToken() {
    if (!currentUser?.accessToken) throw new Error('Authentication required');
    if (!isAccessTokenExpired(currentUser.accessToken)) return currentUser.accessToken;
    if (!currentUser.refreshToken) throw new Error('Session expired');
    const response = await fetch(`${USER_API_BASE}/api/users/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: currentUser.refreshToken })
    });
    if (!response.ok) { logoutUser(); throw new Error('Session expired. Please sign in again.'); }
    const payload = await response.json();
    currentUser.accessToken = payload.accessToken;
    persistCurrentUser(currentUser);
    return payload.accessToken;
  }
  function avatarStorageKey() { return currentUser?.id ? `pulsepass-avatar-${currentUser.id}` : null; }
  function getSavedAvatar() {
    const key = avatarStorageKey();
    if (!key) return null;
    try { return localStorage.getItem(key); } catch (error) { return null; }
  }
  function updateAuthButton() {
    const btn = $('authToggleBtn');
    if (!btn) return;
    if (currentUser) {
      const name = currentUser.fullName || currentUser.email || 'User';
      const dataUrl = getSavedAvatar();
      const avatarInner = dataUrl ? `<img src="${dataUrl}" alt="" />` : escapeHtml(initialsFor(name));
      btn.classList.add('is-user');
      btn.innerHTML = `<span class="btn-avatar">${avatarInner}</span><span class="btn-name">${escapeHtml(firstNameOf(name))}</span>`;
    } else {
      btn.classList.remove('is-user');
      btn.textContent = t('nav.signin');
    }
  }
  function persistCurrentUser(user) {
    if (user) user.role = decodeJwtPayload(user.accessToken)?.role || user.role || 'USER';
    currentUser = user;
    if (user) localStorage.setItem('pulsepass-user', JSON.stringify(user));
    else localStorage.removeItem('pulsepass-user');
    updateAuthButton();
    updateAdminPanelVisibility();
    updateCtaVisibility();
  }
  function logoutUser() {
    persistCurrentUser(null);
    ownedSeats = new Map();
    dashboardProfile = null;
    dashboardOrders = [];
    dashProfileLoaded = false;
    dashOrdersLoaded = false;
    notify('info', t('dash.loggedOut'), '');
    showLanding();
  }
  function updateAdminPanelVisibility() {
    $('adminPanel')?.classList.toggle('hidden', currentUser?.role !== 'ADMIN');
  }
  function updateCtaVisibility() {
    $('ctaSection')?.classList.toggle('hidden', Boolean(currentUser));
  }

  /* ================= avatar upload ================= */
  function renderAvatar() {
    const img = $('dashAvatarImg');
    const initials = $('dashAvatarInitials');
    if (!img || !initials) return;
    const name = dashboardProfile?.fullName || currentUser?.fullName || currentUser?.email || 'U';
    initials.textContent = initialsFor(name);
    const dataUrl = getSavedAvatar();
    img.alt = t('dash.avatarAlt');
    if (dataUrl) {
      img.src = dataUrl;
      img.classList.remove('hidden');
      initials.classList.add('hidden');
    } else {
      img.classList.add('hidden');
      img.removeAttribute('src');
      initials.classList.remove('hidden');
    }
    $('avatarRemoveBtn')?.classList.toggle('hidden', !dataUrl);
  }
  function handleAvatarFile(file) {
    if (!file) return;
    const okTypes = ['image/png', 'image/jpeg', 'image/webp'];
    if (!okTypes.includes(file.type)) { notify('error', t('dash.avatarBadType'), t('dash.avatarBadTypeD')); return; }
    if (file.size > 5 * 1024 * 1024) { notify('error', t('dash.avatarTooLarge'), t('dash.avatarTooLargeD')); return; }
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      try {
        const SIZE = 256;
        const canvas = document.createElement('canvas');
        canvas.width = SIZE; canvas.height = SIZE;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, SIZE, SIZE);
        const scale = Math.max(SIZE / img.width, SIZE / img.height);
        const w = img.width * scale, h = img.height * scale;
        ctx.drawImage(img, (SIZE - w) / 2, (SIZE - h) / 2, w, h);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
        const key = avatarStorageKey();
        if (!key) throw new Error('no user');
        localStorage.setItem(key, dataUrl);
        renderAvatar();
        updateAuthButton();
        notify('success', t('dash.avatarSaved'), t('dash.avatarSavedD'));
      } catch (error) {
        notify('error', t('dash.avatarFail'), t('dash.avatarFailD'));
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      notify('error', t('dash.avatarFail'), t('dash.avatarFailD'));
    };
    img.src = url;
  }

  /* ================= dashboard ================= */
  function setProfileCardState(state) {
    $('pcSkeleton')?.classList.toggle('hidden', state !== 'loading');
    $('pcError')?.classList.toggle('hidden', state !== 'error');
    $('pcContent')?.classList.toggle('hidden', state !== 'ready');
  }
  function setOrdersState(state) {
    $('ordersSkeleton')?.classList.toggle('hidden', state !== 'loading');
    $('ordersError')?.classList.toggle('hidden', state !== 'error');
    const showLists = state === 'ready';
    $('dashUpcomingList')?.classList.toggle('hidden', !(showLists && activeDashTab === 'upcoming'));
    $('dashPastList')?.classList.toggle('hidden', !(showLists && activeDashTab === 'past'));
  }
  function setDashStatsLoading(isLoading) {
    $('dashStats')?.classList.toggle('is-loading', isLoading);
  }
  function setDashStatsError() {
    setDashStatsLoading(false);
    ['dashStatTickets', 'dashStatUpcoming', 'dashStatSpent'].forEach((id) => { const el = $(id); if (el) el.textContent = '—'; });
  }
  function openDashboardView() {
    if (!currentUser) { openAuthModal('login'); return; }
    document.body.classList.add('dashboard-mode');
    $('dashboardView')?.classList.remove('hidden');
    window.scrollTo({ top: 0 });
    dashProfileLoaded = false;
    dashOrdersLoaded = false;
    setProfileCardState('loading');
    setOrdersState('loading');
    setDashStatsLoading(true);
    loadDashProfile();
    loadDashOrders();
    if (currentUser?.role === 'ADMIN') Promise.all([loadAdminEvents(), loadAiUsage()]);
  }
  function showLanding() {
    document.body.classList.remove('dashboard-mode');
    $('dashboardView')?.classList.add('hidden');
  }
  async function loadDashProfile() {
    if (!currentUser?.id) return;
    setProfileCardState('loading');
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${USER_API_BASE}/api/users/profile/${currentUser.id}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      dashboardProfile = await response.json();
      dashProfileLoaded = true;
      setProfileCardState('ready');
      renderProfileCard();
    } catch (error) {
      console.error('Failed to load profile:', error);
      setProfileCardState('error');
    }
  }
  async function loadDashOrders() {
    if (!currentUser?.id) return;
    setOrdersState('loading');
    setDashStatsLoading(true);
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const orders = await response.json();
      dashboardOrders = Array.isArray(orders) ? orders : [];
      dashOrdersLoaded = true;
      setOrdersState('ready');
      setDashStatsLoading(false);
      renderOrders(dashboardOrders);
    } catch (error) {
      console.error('Failed to load orders:', error);
      setOrdersState('error');
      setDashStatsError();
    }
  }
  function renderProfileCard() {
    if (!dashboardProfile) return;
    const p = dashboardProfile;
    const name = p.fullName || p.email || 'User';
    $('dashName').textContent = name;
    const since = p.createdAt ? `${t('dash.since')} ${new Date(p.createdAt).toLocaleDateString(localeTag())}` : '';
    $('dashMeta').textContent = `${p.email || ''}${since ? ` · ${since}` : ''}`;
    $('dashRoleBadge')?.classList.toggle('hidden', currentUser?.role !== 'ADMIN');
    renderAvatar();
  }
  function renderOrders(orders) {
    dashboardOrders = Array.isArray(orders) ? orders : [];
    const now = Date.now();
    let upcoming = 0, spent = 0;
    const rowsUp = [], rowsPast = [];
    dashboardOrders.forEach((order) => {
      spent += Number(order.totalPrice || 0);
      const ev = allEvents.find((e) => String(e.id) === String(order.eventId));
      const evTime = ev ? new Date(ev.eventDate).getTime() : null;
      const entry = { order, ev };
      if (evTime != null && evTime < now) rowsPast.push(entry);
      else { rowsUp.push(entry); upcoming++; }
    });
    $('dashStatTickets').textContent = String(dashboardOrders.length);
    $('dashStatUpcoming').textContent = String(upcoming);
    $('dashStatSpent').textContent = `$${Math.round(spent).toLocaleString(localeTag())}`;
    $('tabCountUpcoming').textContent = String(rowsUp.length);
    $('tabCountPast').textContent = String(rowsPast.length);
    renderTicketList($('dashUpcomingList'), rowsUp, false);
    renderTicketList($('dashPastList'), rowsPast, true);
  }
  function renderTicketList(container, rows, isPast) {
    if (!container) return;
    if (!rows.length) {
      container.innerHTML = `
      <div class="tickets-empty">
        <svg class="te-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M3 10h18M7 14h4"/></svg>
        <p>${escapeHtml(t(isPast ? 'dash.emptyPast' : 'dash.empty'))}</p>
        ${isPast ? '' : `<button type="button" class="primary-btn btn-sm" data-go-events>${escapeHtml(t('dash.findEvents'))}</button>`}
      </div>`;
      return;
    }
    container.innerHTML = rows.map(({ order, ev }) => {
      const seatLabel = String(order.seatLabel || '');
      const row = seatLabel.replace(/\d+$/, '');
      const seatNumber = Number(seatLabel.replace(/\D/g, '')) || 0;
      const when = ev ? fmtEventWhen(ev.eventDate) : new Date(order.purchasedAt).toLocaleDateString(localeTag());
      const { city, venue } = ev ? splitLocation(ev.location) : { city: '', venue: '' };
      const orderNo = order.id ? String(order.id).slice(0, 8) : '';
      const thumbStyle = ev?.imageUrl ? ` style="background-image:url('${escapeHtml(ev.imageUrl)}')"` : '';
      const thumbIcon = ev?.imageUrl ? '' : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>`;
      return `
    <article class="ticket-card ${isPast ? 'past' : ''}">
      <div class="tk-thumb ${ev ? coverClass(ev) : ''}"${thumbStyle}>${thumbIcon}</div>
      <div class="tk-info">
        <h4>${escapeHtml(order.eventName || '—')}</h4>
        <p class="tk-when">${escapeHtml(when)}</p>
        <p class="tk-venue">${escapeHtml(venue ? `${city} · ${venue}` : city)}</p>
        <span class="tk-seat">${escapeHtml(t('dash.seatRow'))} ${escapeHtml(row)} · ${escapeHtml(t('dash.seatNo'))} ${seatNumber}</span>
      </div>
      <div class="tk-stub">
        <span class="tk-price">$${Number(order.totalPrice || 0).toFixed(2)}</span>
        <div class="tk-barcode" aria-hidden="true"></div>
        ${orderNo ? `<span class="tk-order">${escapeHtml(t('dash.order'))}${escapeHtml(orderNo)}</span>` : ''}
        ${isPast ? '' : `<button type="button" class="tk-return"
          data-order-event-id="${escapeHtml(order.eventId || '')}"
          data-order-seat-row="${escapeHtml(row)}"
          data-order-seat-number="${seatNumber}">${escapeHtml(t('dash.return'))}</button>`}
      </div>
    </article>`;
    }).join('');
  }
  function askReturnTicket(eventId, row, seatNumber) {
    const found = allEvents.find((item) => String(item.id) === String(eventId));
    if (!found) { notify('error', t('dash.notFoundT'), t('dash.notFoundD')); return; }
    pendingReturn = { eventId, row, seatNumber };
    $('rcBody').textContent = `${found.name} · ${t('dash.seatRow')} ${row} · ${t('dash.seatNo')} ${seatNumber}`;
    openModal('returnConfirm');
  }

  /* ================= owned seats ================= */
  async function loadOwnedSeats() {
    if (!currentUser?.id) { ownedSeats = new Map(); return; }
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const orders = await response.json();
      const next = new Map();
      orders.forEach((order) => {
        const key = String(order.eventId);
        if (!next.has(key)) next.set(key, new Set());
        next.get(key).add(String(order.seatLabel || '').trim().toUpperCase());
      });
      ownedSeats = next;
    } catch (error) { console.warn('Could not load owned seats:', error); }
  }
  function removeUserOwnedSeat(eventId, row, seatNumber) {
    const seats = ownedSeats.get(String(eventId));
    if (!seats) return;
    seats.delete(getSeatKey(row, seatNumber));
    if (!seats.size) ownedSeats.delete(String(eventId));
  }
  async function orderStillExists(eventId, seatLabel) {
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${USER_API_BASE}/api/users/${currentUser.id}/orders`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) return true;
      const orders = await response.json();
      return orders.some((o) => String(o.eventId) === String(eventId) && String(o.seatLabel) === String(seatLabel));
    } catch (error) { return true; }
  }
  async function waitForOrderRemoval(eventId, seatLabel, timeoutMs = 5000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (!(await orderStillExists(eventId, seatLabel))) return true;
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
    return false;
  }
  async function ticketReturnClicked(event, row, seatNumber) {
    if (!currentUser?.id) return false;
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${BOOKING_API_BASE}/api/bookings/return`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ eventId: event.id, seatRow: row, seatNumber: Number(seatNumber) })
      });
      if (!response.ok) throw new Error((await response.text()) || 'Ticket return failed');
      removeUserOwnedSeat(event.id, row, seatNumber);
      notify('success', t('toast.retOkT'), `${getSeatKey(row, seatNumber)} ${t('toast.retOkD')}`);
      await loadEvents();
      return true;
    } catch (error) {
      console.error('Ticket return failed:', error);
      notify('error', t('toast.retFailT'), t('toast.retFailD'));
      return false;
    }
  }

  /* ================= payment ================= */
  function openPaymentModal() {
    if (!selectedEvent || selectedSeats.length === 0) { notify('error'); return; }
    if (!currentUser) {
      openAuthModal('login');
      const authStatus = $('authStatus');
      if (authStatus) authStatus.textContent = t('auth.needPurchase');
      return;
    }
    const seats = selectedSeats.filter((s) => s.eventId === selectedEvent.id);
    const total = seats.reduce((sum, s) => sum + Number(s.price || 0), 0);
    $('paymentSummary').textContent = `${seats.map((s) => s.label).join(', ')} • $${total}`;
    closeModal('event');
    openModal('payment');
  }
  function markSeatAsPaying(eventId, row, seatNumber) {
    const id = String(eventId);
    if (!seatsBeingPaid.has(id)) seatsBeingPaid.set(id, new Set());
    seatsBeingPaid.get(id).add(getSeatKey(row, seatNumber));
  }
  function unmarkSeatAsPaying(eventId, row, seatNumber) {
    const eventSeats = seatsBeingPaid.get(String(eventId));
    if (!eventSeats) return;
    eventSeats.delete(getSeatKey(row, seatNumber));
    if (!eventSeats.size) seatsBeingPaid.delete(String(eventId));
  }
  async function pollBookingStatus(bookingId) {
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
      try {
        const token = await ensureValidAccessToken();
        const response = await fetch(`${BOOKING_API_BASE}/api/bookings/${bookingId}/status`, { headers: { Authorization: `Bearer ${token}` } });
        if (!response.ok) return 'PENDING';
        const payload = await response.json();
        if (payload.status === 'PAID' || payload.status === 'CANCELLED') return payload.status;
      } catch (error) { console.error('Status poll error:', error); }
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    return 'PENDING';
  }
  async function handlePaymentSubmit(paymentForm) {
    if (!selectedEvent || selectedSeats.length === 0) { notify('error'); return; }
    const paymentEvent = selectedEvent;
    const paymentSeats = selectedSeats.filter((s) => s.eventId === paymentEvent.id).map((s) => ({ ...s }));
    const submitButton = paymentForm.querySelector('button[type="submit"]');
    const originalText = submitButton.textContent;
    submitButton.disabled = true;
    submitButton.textContent = t('pay.processing');
    paymentSeats.forEach((s) => markSeatAsPaying(paymentEvent.id, s.row, s.seatNumber));
    openEventModal(paymentEvent);
    try {
      const results = [];
      for (const seat of paymentSeats) {
        const token = await ensureValidAccessToken();
        const bookingResponse = await fetch(`${BOOKING_API_BASE}/api/bookings`, {
          method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ eventId: paymentEvent.id, seatRow: seat.row, seatNumber: seat.seatNumber })
        });
        if (!bookingResponse.ok) throw new Error((await bookingResponse.text()) || 'Booking failed');
        const bookingData = await bookingResponse.json();
        results.push({ seat, status: await pollBookingStatus(bookingData.bookingId) });
      }
      const paid = results.filter((r) => r.status === 'PAID');
      if (paid.length > 0) {
        for (const { seat } of paid) {
          try {
            const token = await ensureValidAccessToken();
            await fetch(`${USER_API_BASE}/api/users/orders`, {
              method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
              body: JSON.stringify({ eventId: paymentEvent.id, eventName: paymentEvent.name, seatLabel: `${seat.row}${seat.seatNumber}`, totalPrice: Number(seat.price || 0) })
            });
          } catch (orderError) { console.warn('Could not store order history:', orderError); }
        }
        await loadOwnedSeats();
        notify('success', t('toast.payOkT'), t('toast.payOkD'));
        closeModal('payment');
        closeModal('event');
        selectedSeats = selectedSeats.filter((s) => s.eventId !== paymentEvent.id);
        selectedEvent = null;
        updateBuyButton();
        return;
      }
      notify('error');
    } catch (error) {
      console.error('Booking/payment error:', error);
      notify('error');
    } finally {
      paymentSeats.forEach((s) => unmarkSeatAsPaying(paymentEvent.id, s.row, s.seatNumber));
      submitButton.disabled = false;
      submitButton.textContent = originalText;
      await loadEvents();
      if (selectedEvent && !$('eventModal').classList.contains('hidden')) {
        const refreshed = allEvents.find((e) => String(e.id) === String(selectedEvent.id));
        if (refreshed) openEventModal(refreshed);
      }
    }
  }

  /* ================= auth modal ================= */
  function setErr(id, msg) {
    const el = $(id);
    if (!el) return;
    el.textContent = msg || '';
    el.hidden = !msg;
  }
  function clearAuthErrors() {
    ['nameErr', 'emailErr', 'passErr', 'confirmErr'].forEach((id) => setErr(id, ''));
    const status = $('authStatus');
    if (status) status.textContent = '';
  }
  function resetPwMeter() {
    const fill = $('pwBarFill');
    if (fill) { fill.style.width = '0'; fill.className = ''; }
    const text = $('pwBarText');
    if (text) text.textContent = '';
  }
  function openAuthModal(mode = 'login') {
    authMode = mode;
    clearAuthErrors();
    openModal('auth');
    $('registerNameField')?.classList.toggle('hidden', mode !== 'register');
    $('registerConfirmField')?.classList.toggle('hidden', mode !== 'register');
    $('pwMeter')?.classList.toggle('hidden', mode !== 'register');
    if (mode !== 'register') resetPwMeter();
    $('authSubmitBtn').textContent = mode === 'register' ? t('auth.submitRegister') : t('auth.submitLogin');
    document.querySelectorAll('.auth-tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.authView === mode));
  }
  function pwScore(value) {
    let score = 0;
    if (value.length >= 8) score++;
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
    if (/\d/.test(value)) score++;
    if (/[^A-Za-z0-9]/.test(value)) score++;
    return score;
  }
  function updateStrength() {
    if (authMode !== 'register') return;
    const value = $('authPassword')?.value || '';
    const fill = $('pwBarFill'), text = $('pwBarText');
    if (!fill || !text) return;
    if (!value) { fill.style.width = '0'; fill.className = ''; text.textContent = ''; return; }
    const level = Math.min(pwScore(value), 3);
    fill.style.width = `${(level + 1) * 25}%`;
    fill.className = `l${level}`;
    text.textContent = `${t('auth.pwHint')}: ${t(`auth.pw${level}`)}`;
  }

  function formatDateTimeInput(value) {
    const date = value ? new Date(value) : new Date(Date.now() + 30 * 86400000);
    if (!value) date.setHours(19, 30, 0, 0);
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  function showAdminStatus(message, isError = false) {
    const status = $('adminFormStatus');
    if (!status) return;
    status.textContent = message;
    status.classList.toggle('error', isError);
  }
  function openAdminEventForm(eventToEdit = null) {
    const form = $('adminEventForm');
    if (!form) return;
    editingEventId = eventToEdit?.id || null;
    form.reset();
    form.elements.namedItem('name').value = eventToEdit?.name || '';
    form.elements.namedItem('artist').value = eventToEdit?.artist || '';
    form.elements.namedItem('location').value = eventToEdit?.location || '';
    form.elements.namedItem('eventDate').value = formatDateTimeInput(eventToEdit?.eventDate);
    setSeatConfigVisible(!eventToEdit);
    updateSeatSummary();
    $('adminFormTitle').textContent = eventToEdit ? `${t('admin.edit')}: ${eventToEdit.name}` : t('admin.new');
    $('adminSaveEvent').textContent = eventToEdit ? t('admin.save') : t('admin.create');
    showAdminStatus('');
    form.classList.remove('hidden');
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
function renderAdminEvents(events) {
  const list = $('adminEventList');
  if (!list) return;
  list.innerHTML = events.length
      ? events.map((event) => `
      <article class="admin-event-row">
        <div class="admin-event-summary">
          <strong>${escapeHtml(event.name)}</strong>
          <span>${escapeHtml(event.artist || '')} · ${escapeHtml(event.location)} · ${escapeHtml(new Date(event.eventDate).toLocaleString(localeTag()))}</span>
          <span>${event.seats?.length || 0} ${escapeHtml(t('admin.seatsWord'))}</span>
        </div>
        <div class="admin-event-actions">
          <button type="button" class="ghost-btn btn-sm" data-admin-edit="${escapeHtml(event.id)}">${escapeHtml(t('admin.edit'))}</button>
          <button type="button" class="ghost-btn btn-sm danger" data-admin-delete="${escapeHtml(event.id)}">${escapeHtml(t('admin.unpublish'))}</button>
        </div>
      </article>`).join('')
      : `<p class="empty-state">${escapeHtml(t('admin.noEvents'))}</p>`;
}
async function loadAiUsage() {
  const box = $('aiUsage');
  if (!box || currentUser?.role !== 'ADMIN') return;
  box.textContent = '…';
  try {
    const token = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/ai/stats`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const s = await response.json();
    box.innerHTML = `${escapeHtml(t('admin.aiToday'))} (UTC <strong>${escapeHtml(s.dateUtc)}</strong>):
      <strong>${s.globalUsed}/${s.globalLimit}</strong> ${escapeHtml(t('admin.aiTotal'))},
      ${escapeHtml(t('admin.aiYou'))}: <strong>${s.userUsed}/${s.userLimit}</strong> ·
      LLM: ${s.llmCalls} · ${escapeHtml(t('admin.aiCache'))}: ${s.cacheHits} · ${escapeHtml(t('admin.aiFallback'))}: ${s.fallbacks} ·
      ${escapeHtml(t('admin.aiErrors'))}: ${s.errors} · ~<strong>${s.estimatedNeurons}</strong> ${escapeHtml(t('admin.aiNeurons'))} ·
      ${escapeHtml(t('admin.aiEmbed'))}: ${s.embeddingCalls}`;
  } catch (error) { box.textContent = t('admin.aiUnavailable'); }
}
async function loadAdminEvents() {
  if (currentUser?.role !== 'ADMIN') return;
  const list = $('adminEventList');
  if (list) list.innerHTML = `<p class="empty-state">${escapeHtml(t('admin.loading'))}</p>`;
  try {
    const token = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/events`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    adminEvents = dedupeEvents(await response.json());
    renderAdminEvents(adminEvents);
  } catch (error) {
    console.error('Failed to load admin events:', error);
    if (list) list.innerHTML = `<p class="empty-state">${escapeHtml(t('admin.loadFail'))}</p>`;
  }
}
function toRowLabel(rowIndex) {
  let label = '', value = rowIndex;
  do { label = String.fromCharCode(65 + (value % 26)) + label; value = Math.floor(value / 26) - 1; } while (value >= 0);
  return label;
}
function readSeatConfig() {
  const f = $('adminEventForm').elements;
  return {
    rows: Number(f.namedItem('seatRows').value), perRow: Number(f.namedItem('seatsPerRow').value),
    base: Number(f.namedItem('seatPrice').value), step: Number(f.namedItem('rowPriceStep').value) || 0
  };
}
function seatConfigError({ rows, perRow, base, step }) {
  if (!Number.isInteger(rows) || rows < 1 || !Number.isInteger(perRow) || perRow < 1) return t('admin.seatErr1');
  if (rows * perRow > MAX_SEATS) return `${t('admin.seatErr2')} ${rows * perRow})`;
  if (!(base > 0) || step < 0) return t('admin.seatErr3');
  return '';
}
function buildSeats({ rows, perRow, base, step }) {
  const seats = [];
  for (let r = 0; r < rows; r++) {
    const price = Math.round((base + r * step) * 100) / 100;
    for (let n = 1; n <= perRow; n++) seats.push({ row: toRowLabel(r), seatNumber: n, price });
  }
  return seats;
}
function setSeatConfigVisible(visible) {
  const box = $('adminSeatConfig');
  if (!box) return;
  box.classList.toggle('hidden', !visible);
  box.querySelectorAll('input').forEach((input) => { input.disabled = !visible; });
}
function updateSeatSummary() {
  const summary = $('adminSeatSummary');
  if (!summary) return;
  const config = readSeatConfig();
  const error = seatConfigError(config);
  summary.classList.toggle('error', Boolean(error));
  if (error) { summary.textContent = error; return; }
  const last = toRowLabel(config.rows - 1);
  const max = config.base + (config.rows - 1) * config.step;
  summary.textContent = `${config.rows * config.perRow} ${t('admin.seatsWord')} · ${t('admin.rowsRange')} A–${last} · ${t('admin.priceRange')} $${config.base.toFixed(2)}–$${max.toFixed(2)}`;
}
async function saveAdminEvent(form) {
  const formData = new FormData(form);
  const payload = {
    name: formData.get('name').trim(), artist: formData.get('artist').trim(),
    location: formData.get('location').trim()
  };
  const rawDate = formData.get('eventDate');
  const parsed = rawDate ? new Date(rawDate) : null;
  payload.eventDate = (parsed && !Number.isNaN(parsed.getTime())) ? parsed.toISOString() : rawDate;
  if (!editingEventId) {
    const config = readSeatConfig();
    const error = seatConfigError(config);
    if (error) throw new Error(error);
    payload.seats = buildSeats(config);
  }
  const isEditing = Boolean(editingEventId);
  const endpoint = isEditing ? `${API_BASE}/api/events/${editingEventId}` : `${API_BASE}/api/events`;
  const token = await ensureValidAccessToken();
  const response = await fetch(endpoint, {
    method: isEditing ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    const errorPayload = await response.json().catch(() => ({}));
    throw new Error(errorPayload.message || errorPayload.error || `Save failed (HTTP ${response.status})`);
  }
  closeAdminEventForm();
  await Promise.all([loadEvents(), loadAdminEvents()]);
}

async function searchEvents(event) {
  event.preventDefault();
  const params = new URLSearchParams();
  const artist = $('artistFilter').value.trim();
  const location = $('locationFilter').value.trim();
  const date = $('dateFilter').value;
  if (artist) params.set('artist', artist);
  if (location) params.set('location', location);
  if (date) params.set('date', date);
  activeFilter = 'all';
  updateChips();
  if (!params.toString()) { await loadEvents(); return; }
  try {
    const response = await fetch(`${API_BASE}/api/events?${params.toString()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    allEvents = dedupeEvents(await response.json());
    await hydrateSeatStatusesForEvents(allEvents);
    currentPage = 1;
    refreshView('events.noResults');
  } catch (error) {
    console.error('Search failed:', error);
    allEvents = [];
    refreshView('events.loadError');
  }
}
async function loadEvents() {
  renderSkeletons();
  try {
    const response = await fetch(`${API_BASE}/api/events`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    allEvents = dedupeEvents(await response.json());
    await hydrateSeatStatusesForEvents(allEvents);
    currentPage = 1;
    refreshView();
  } catch (error) {
    console.error('Failed to load events:', error);
    allEvents = [];
    refreshView('events.loadError');
  }
}

/* ================= AI assistant ================= */
function appendAiMessage(role, text) {
  const box = $('aiChatMessages');
  if (!box) return null;
  const element = document.createElement('div');
  element.className = `ai-message ${role}`;
  element.textContent = text;
  box.appendChild(element);
  box.scrollTop = box.scrollHeight;
  return element;
}
function appendAiAnswer(answer, events) {
  const box = $('aiChatMessages');
  if (!box) return;
  const bubble = document.createElement('div');
  bubble.className = 'ai-message assistant';
  if (answer) {
    const intro = document.createElement('p');
    intro.className = 'ai-answer-intro';
    intro.textContent = answer;
    bubble.appendChild(intro);
  }
  if (events.length) {
    const list = document.createElement('ul');
    list.className = 'ai-answer-list';
    events.forEach((event) => {
      const item = document.createElement('li');
      item.className = 'ai-answer-item';
      item.dataset.aiEvent = event.id;
      item.tabIndex = 0;
      const thumb = document.createElement('div');
      thumb.className = 'ai-answer-thumb';
      if (event.imageUrl) thumb.style.backgroundImage = `url("${event.imageUrl}")`;
      const body = document.createElement('div');
      body.className = 'ai-answer-body';
      const name = document.createElement('strong');
      name.textContent = event.name;
      const meta = document.createElement('span');
      meta.textContent = `${event.location} · ${formatLongDate(event.eventDate)}`;
      const price = document.createElement('span');
      price.textContent = `${t('ai.from')} $${Number(event.priceFrom || 0).toFixed(2)}`;
      body.append(name, meta, price);
      item.append(thumb, body);
      list.appendChild(item);
    });
    bubble.appendChild(list);
  }
  box.appendChild(bubble);
  box.scrollTop = box.scrollHeight;
}
async function openEventById(eventId) {
  let event = allEvents.find((item) => String(item.id) === String(eventId));
  if (!event) {
    try {
      const response = await fetch(`${API_BASE}/api/events`);
      if (response.ok) {
        const events = dedupeEvents(await response.json());
        event = events.find((item) => String(item.id) === String(eventId));
      }
    } catch (error) { console.warn('Could not load event for AI card:', error); }
  }
  if (event) openEventModal(event);
}
async function askAi(message) {
  const submit = $('aiChatSubmit');
  appendAiMessage('user', message);
  if (!currentUser) {
    appendAiMessage('assistant', t('ai.needAuth'));
    openAuthModal('login');
    return;
  }
  const pending = document.createElement('div');
  pending.className = 'ai-message assistant';
  pending.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
  const box = $('aiChatMessages');
  box.appendChild(pending);
  box.scrollTop = box.scrollHeight;
  if (submit) submit.disabled = true;
  try {
    const token = await ensureValidAccessToken();
    const response = await fetch(`${API_BASE}/api/ai/chat`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ message })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || `AI request failed (HTTP ${response.status})`);
    pending.remove();
    appendAiAnswer(payload.answer || t('ai.error'), payload.events || []);
  } catch (error) {
    pending.remove();
    appendAiMessage('error', error.message || t('ai.error'));
  } finally {
    if (submit) submit.disabled = false;
  }
}

/* ================= init ================= */
document.addEventListener('DOMContentLoaded', () => {
  currentUser = readCurrentUser();
  persistCurrentUser(currentUser);

  applyLanguage(currentLang);

  // v17: min-дата в поиске = сегодня. Поле пустое, не заполняется само.
  (function initDateMin() {
    const el = $('dateFilter');
    if (!el) return;
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    el.min = `${yyyy}-${mm}-${dd}`;
  })();

  loadEvents();
  if (currentUser) loadOwnedSeats();

  $('langRu')?.addEventListener('click', () => applyLanguage('ru'));
  $('langEn')?.addEventListener('click', () => applyLanguage('en'));

  $('brandHome')?.addEventListener('click', (e) => {
    if (document.body.classList.contains('dashboard-mode')) { e.preventDefault(); showLanding(); }
  });
  $('dashBackBtn')?.addEventListener('click', showLanding);
  document.querySelectorAll('#navLinks a').forEach(link => {
    link.addEventListener('click', (e) => {
      if (document.body.classList.contains('dashboard-mode')) {
        e.preventDefault();
        showLanding();
        const targetId = link.getAttribute('href');
        if (targetId && targetId.startsWith('#')) {
          const targetEl = document.querySelector(targetId);
          if (targetEl) {
            setTimeout(() => {
              targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }, 60);
          }
        }
      }
    });
  });
  $('dashLogout')?.addEventListener('click', logoutUser);
  $('authToggleBtn')?.addEventListener('click', () => currentUser ? openDashboardView() : openAuthModal('login'));
  $('ctaSignup')?.addEventListener('click', () => openAuthModal('register'));

  document.querySelectorAll('.dash-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      activeDashTab = tab.dataset.dashTab;
      document.querySelectorAll('.dash-tab').forEach((x) => x.classList.toggle('active', x.dataset.dashTab === activeDashTab));
      if (dashOrdersLoaded) setOrdersState('ready');
    });
  });

  $('dashProfileRetry')?.addEventListener('click', loadDashProfile);
  $('dashOrdersRetry')?.addEventListener('click', loadDashOrders);

  $('avatarChangeBtn')?.addEventListener('click', () => $('avatarUploadInput')?.click());
  $('avatarUploadInput')?.addEventListener('change', (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) handleAvatarFile(file);
  });
  $('avatarRemoveBtn')?.addEventListener('click', () => {
    const key = avatarStorageKey();
    if (key) { try { localStorage.removeItem(key); } catch (error) { /* ignore */ } }
    renderAvatar();
    updateAuthButton();
  });

  document.addEventListener('click', (e) => {
    if (e.target.closest('[data-go-events]')) {
      showLanding();
      setTimeout(() => $('eventsSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60);
    }
  });
  document.addEventListener('click', (e) => {
    const stub = e.target.closest('[data-stub]');
    if (!stub) return;
    e.preventDefault();
    notify('info', t('stub.title'), t('stub.text'));
  });
  document.addEventListener('click', (e) => {
    const trigger = e.target.closest('[data-open-event]');
    if (!trigger) return;
    const found = allEvents.find((item) => String(item.id) === String(trigger.dataset.openEvent));
    if (found) openEventModal(found);
  });
  document.addEventListener('click', (e) => {
    const aiCard = e.target.closest('[data-ai-event]');
    if (aiCard) openEventById(aiCard.dataset.aiEvent);
  });
  document.addEventListener('click', (e) => {
    const q = e.target.closest('.faq-q');
    if (q) q.closest('.faq-item')?.classList.toggle('open');
  });

  ['dashUpcomingList', 'dashPastList'].forEach((id) => {
    $(id)?.addEventListener('click', (event) => {
      const button = event.target.closest('.tk-return');
      if (!button) return;
      askReturnTicket(button.dataset.orderEventId, button.dataset.orderSeatRow, Number(button.dataset.orderSeatNumber || 0));
    });
  });
  $('rcConfirmBtn')?.addEventListener('click', async () => {
    if (!pendingReturn) { closeModal('returnConfirm'); return; }
    const button = $('rcConfirmBtn');
    const { eventId, row, seatNumber } = pendingReturn;
    button.disabled = true;
    closeModal('returnConfirm');
    try {
      const found = allEvents.find((item) => String(item.id) === String(eventId));
      const returned = found ? await ticketReturnClicked(found, row, seatNumber) : false;
      if (returned) await waitForOrderRemoval(eventId, `${row}${seatNumber}`);
    } finally {
      pendingReturn = null;
      button.disabled = false;
      if (document.body.classList.contains('dashboard-mode')) await loadDashOrders();
    }
  });

  $('filterChips')?.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-filter]');
    if (!chip) return;
    activeFilter = chip.dataset.filter;
    currentPage = 1;
    updateChips();
    refreshView(lastEmptyKey);
    $('eventsSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  $('searchForm')?.addEventListener('submit', searchEvents);
  $('eventPagination')?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-page]');
    if (!button || button.disabled) return;
    currentPage = Number(button.dataset.page);
    refreshView(lastEmptyKey);
    $('eventsSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) { entry.target.classList.add('reveal-in'); revealObserver.unobserve(entry.target); }
    });
  }, { threshold: 0.12 });
  document.querySelectorAll('.reveal').forEach((el) => revealObserver.observe(el));

  const statsObserver = new IntersectionObserver((entries) => {
    if (!entries.some((entry) => entry.isIntersecting)) return;
    statsVisible = true;
    statsObserver.disconnect();
    if (allEvents.length) animateStats();
  }, { threshold: 0.4 });
  const statsStrip = $('statsStrip');
  if (statsStrip) statsObserver.observe(statsStrip);

  ['auth', 'event', 'payment', 'returnConfirm'].forEach((name) => {
    document.querySelectorAll(`[data-close="${name}"]`).forEach((button) => {
      button.addEventListener('click', () => closeModal(name));
    });
  });
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    ['returnConfirm', 'payment', 'event', 'auth'].forEach((name) => {
      if ($(name) && !$(name).classList.contains('hidden')) closeModal(name);
    });
  });

  $('aiChatForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = $('aiChatInput');
    const message = input.value.trim();
    if (!message) return;
    input.value = '';
    await askAi(message);
  });
  $('aiExamples')?.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-ai-ex]');
    if (chip) askAi(t(chip.dataset.aiEx));
  });

  document.querySelectorAll('.pw-toggle').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = $(btn.dataset.pwToggle);
      if (input) input.type = input.type === 'password' ? 'text' : 'password';
      syncPwToggleLabels();
    });
  });
  ['registerName', 'authEmail', 'authPassword', 'registerConfirm'].forEach((id) => {
    $(id)?.addEventListener('input', () => {
      if (id === 'registerName') setErr('nameErr', '');
      if (id === 'authEmail') setErr('emailErr', '');
      if (id === 'authPassword') { setErr('passErr', ''); updateStrength(); }
      if (id === 'registerConfirm') setErr('confirmErr', '');
    });
  });
  $('authForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearAuthErrors();
    const email = $('authEmail')?.value?.trim() || '';
    const password = $('authPassword')?.value || '';
    const fullName = $('registerName')?.value?.trim() || '';
    const confirm = $('registerConfirm')?.value || '';
    let valid = true;
    if (authMode === 'register' && fullName.length < 2) { setErr('nameErr', t('auth.enterName')); valid = false; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setErr('emailErr', t('auth.enterEmail')); valid = false; }
    if (password.length < 6) { setErr('passErr', t('auth.shortPw')); valid = false; }
    if (authMode === 'register' && confirm !== password) { setErr('confirmErr', t('auth.mismatch')); valid = false; }
    if (!valid) return;
    const endpoint = authMode === 'register' ? 'register' : 'login';
    const submitBtn = $('authSubmitBtn');
    submitBtn.disabled = true;
    submitBtn.textContent = authMode === 'register' ? t('auth.creating') : '…';
    try {
      const response = await fetch(`${USER_API_BASE}/api/users/${endpoint}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, fullName, password })
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.message || payload.error || t('auth.authFailed'));
      persistCurrentUser(payload);
      await loadOwnedSeats();
      notify('success', authMode === 'register' ? t('auth.registered') : t('auth.welcome'), '');
      closeModal('auth');
      if (selectedEvent && selectedSeats.length > 0) openPaymentModal();
      else openDashboardView();
    } catch (error) {
      console.error('Auth failed:', error);
      const status = $('authStatus');
      if (status) status.textContent = error.message || t('auth.authFailed');
      notify('error', t('auth.authFailed'), error.message || '');
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = authMode === 'register' ? t('auth.submitRegister') : t('auth.submitLogin');
    }
  });

  $('cardNumber')?.addEventListener('input', (e) => {
    e.target.value = e.target.value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
  });
  $('expiry')?.addEventListener('input', (e) => {
    let v = e.target.value.replace(/\D/g, '').slice(0, 4);
    if (v.length > 2) v = `${v.slice(0, 2)}/${v.slice(2)}`;
    e.target.value = v;
  });
  $('cvc')?.addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, '').slice(0, 4); });
  $('buyTicketBtn')?.addEventListener('click', openPaymentModal);
  $('paymentForm')?.addEventListener('submit', (e) => { e.preventDefault(); handlePaymentSubmit(e.currentTarget); });

  $('adminCreateEvent')?.addEventListener('click', () => { $('adminImportForm')?.classList.add('hidden'); openAdminEventForm(); });
  $('adminCancelEdit')?.addEventListener('click', closeAdminEventForm);
  $('adminImportToggle')?.addEventListener('click', () => {
    const form = $('adminImportForm');
    if (!form) return;
    const opening = form.classList.contains('hidden');
    form.classList.toggle('hidden', !opening);
    if (opening) {
      closeAdminEventForm();
      const status = $('adminImportStatus');
      if (status) status.textContent = '';
      form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });
  $('adminImportCancel')?.addEventListener('click', () => $('adminImportForm')?.classList.add('hidden'));
  $('adminImportForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = $('adminImportSubmit');
    const status = $('adminImportStatus');
    const params = new URLSearchParams();
    const keyword = form.elements.namedItem('keyword').value.trim();
    const countryCode = form.elements.namedItem('countryCode').value.trim().toUpperCase();
    const city = form.elements.namedItem('city').value.trim();
    const classificationName = form.elements.namedItem('classificationName').value.trim();
    const sort = form.elements.namedItem('sort').value;
    const size = Number(form.elements.namedItem('size').value) || 20;
    const pages = Number(form.elements.namedItem('pages').value) || 1;
    if (keyword) params.set('keyword', keyword);
    if (countryCode) params.set('countryCode', countryCode);
    if (city) params.set('city', city);
    if (classificationName) params.set('classificationName', classificationName);
    if (sort) params.set('sort', sort);
    params.set('size', size);
    params.set('pages', pages);
    submit.disabled = true;
    status.classList.remove('error');
    status.textContent = t('admin.importing');
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${API_BASE}/api/events/import/ticketmaster?${params.toString()}`, {
        method: 'POST', headers: { Authorization: `Bearer ${token}` }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.message || `Import failed (HTTP ${response.status})`);
      status.textContent = `${t('admin.imported')} ${payload.imported} ${t('admin.of')} ${payload.fetched}. ${t('admin.skipped')}: ${payload.skippedExisting}, ${t('admin.skippedNoDate')}: ${payload.skippedInvalid}.`;
      await Promise.all([loadEvents(), loadAdminEvents()]);
    } catch (error) {
      status.textContent = error.message || t('admin.loadFail');
      status.classList.add('error');
    } finally { submit.disabled = false; }
  });
  $('adminSeatConfig')?.addEventListener('input', updateSeatSummary);
  updateSeatSummary();
  $('adminEventForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const saveButton = $('adminSaveEvent');
    saveButton.disabled = true;
    showAdminStatus(t('admin.saving'));
    try { await saveAdminEvent(event.currentTarget); }
    catch (error) { showAdminStatus(error.message || t('admin.saveFail'), true); }
    finally { saveButton.disabled = false; }
  });
  $('adminEventList')?.addEventListener('click', async (event) => {
    const editButton = event.target.closest('[data-admin-edit]');
    if (editButton) {
      const item = adminEvents.find((e) => String(e.id) === editButton.dataset.adminEdit);
      if (item) openAdminEventForm(item);
      return;
    }
    const deleteButton = event.target.closest('[data-admin-delete]');
    if (!deleteButton || !window.confirm(t('admin.confirmUnpublish'))) return;
    deleteButton.disabled = true;
    try {
      const token = await ensureValidAccessToken();
      const response = await fetch(`${API_BASE}/api/events/${deleteButton.dataset.adminDelete}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      await Promise.all([loadEvents(), loadAdminEvents()]);
    } catch (error) {
      console.error('Unpublish failed:', error);
      notify('error', t('admin.saveFail'), error.message || '');
    } finally { deleteButton.disabled = false; }
  });

  window.addEventListener('resize', () => {
    if (marqueeResizeTimer) clearTimeout(marqueeResizeTimer);
    marqueeResizeTimer = setTimeout(() => buildMarquee(), 200);
  });
  window.addEventListener('load', () => buildMarquee());
});

/* =========================================================
   v15 PATCH — auth / focus trap
   ========================================================= */

function applyAuthMode(mode) {
  authMode = mode === 'register' ? 'register' : 'login';
  const isRegister = authMode === 'register';
  document.querySelectorAll('.auth-tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.authView === authMode));
  $('registerNameField')?.classList.remove('hidden');
  $('registerConfirmField')?.classList.remove('hidden');
  $('registerNameWrap')?.classList.toggle('open', isRegister);
  $('registerConfirmWrap')?.classList.toggle('open', isRegister);
  const pw = $('authPassword');
  if (pw) pw.setAttribute('autocomplete', isRegister ? 'new-password' : 'current-password');
  const submit = $('authSubmitBtn');
  if (submit) submit.textContent = isRegister ? t('auth.submitRegister') : t('auth.submitLogin');
  updateStrength();
}

document.querySelectorAll('.auth-tab').forEach((tab) => {
  tab.addEventListener('click', () => applyAuthMode(tab.dataset.authView));
});

if ($('authModal')) {
  new MutationObserver(() => {
    const modal = $('authModal');
    if (modal && !modal.classList.contains('hidden') && !modal.classList.contains('closing')) applyAuthMode(authMode);
  }).observe($('authModal'), { attributes: true, attributeFilter: ['class'] });
}

function syncPwToggleLabels() {
  document.querySelectorAll('[data-pw-toggle]').forEach((btn) => {
    const input = document.getElementById(btn.dataset.pwToggle);
    if (!input) return;
    const visible = input.type !== 'password';
    btn.setAttribute('aria-label', visible ? t('auth.hidePw') : t('auth.showPw'));
    btn.setAttribute('aria-pressed', String(visible));
  });
}
document.querySelectorAll('[data-pw-toggle]').forEach((btn) => {
  btn.addEventListener('click', () => syncPwToggleLabels());
});

document.addEventListener('keydown', (e) => {
  const modal = document.querySelector('.modal:not(.hidden):not(.closing)');
  if (!modal) return;
  if (e.key === 'Escape') { e.preventDefault(); closeModal(modal.id.replace(/Modal$/, '')); return; }
  if (e.key !== 'Tab') return;
  const visible = (el) => el.offsetParent !== null && !el.closest('[hidden]') && window.getComputedStyle(el).visibility !== 'hidden';
  const items = Array.from(modal.querySelectorAll(FOCUSABLE_SELECTOR)).filter(visible);
  if (!items.length) return;
  const first = items[0], last = items[items.length - 1];
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
});

var seatPollTimer = null;

function startSeatPolling() {
  stopSeatPolling();
  if (!selectedEvent?.id) return;
  seatPollTimer = setInterval(pollSeatStatuses, 12000);
}
function stopSeatPolling() {
  if (seatPollTimer) { clearInterval(seatPollTimer); seatPollTimer = null; }
}
async function pollSeatStatuses() {
  const event = selectedEvent;
  if (!event?.id) return;
  try {
    const response = await fetch(`${BOOKING_API_BASE}/api/bookings/events/${event.id}/seat-status`);
    if (!response.ok || selectedEvent !== event) return;
    const statuses = await response.json();
    const lookup = new Map((statuses || []).map((s) => [`${s.row}-${s.seatNumber}`, s.status]));
    (event.seats || []).forEach((seat) => {
      const backendStatus = lookup.get(`${seat.row}-${seat.seatNumber}`);
      if (!backendStatus) return;
      seat.status = backendStatus;
      seat.available = backendStatus === 'AVAILABLE';
      seat.booked = ['RESERVED', 'SOLD'].includes(backendStatus);
    });
    const seatMap = $('seatMap');
    if (!seatMap) return;
    const owned = ownedSeats.get(String(event.id)) || new Set();
    const takenLabels = new Set();
    getSeatMap(event).forEach((row) => {
      row.seats.forEach((seat) => {
        if (!seat.isSeat) return;
        const btn = seatMap.querySelector(`.seat-btn[data-row="${seat.row}"][data-seat-number="${seat.seatNumber}"]`);
        if (!btn) return;
        const label = `${seat.row}${seat.seatNumber}`;
        const isOwned = owned.has(getSeatKey(seat.row, seat.seatNumber));
        const isSelected = selectedSeats.some((s) => s.eventId === event.id && s.row === seat.row && s.seatNumber === seat.seatNumber);
        if (seat.booked || isOwned) {
          btn.className = 'seat-btn booked'; btn.disabled = true;
          btn.title = `${label} • ${isOwned ? t('toast.yourSeat') : t('toast.booked')}`;
          if (isSelected) takenLabels.add(label);
        } else if (seat.paying) {
          btn.className = 'seat-btn paying'; btn.disabled = true;
          btn.title = `${label} • ${t('toast.payingSeat')}`;
          if (isSelected) takenLabels.add(label);
        } else {
          btn.className = `seat-btn available${isSelected ? ' selected' : ''}`;
          btn.disabled = false;
          btn.title = `${label} • $${seat.price}`;
        }
      });
    });
    if (takenLabels.size) {
      selectedSeats = selectedSeats.filter((s) => !(s.eventId === event.id && takenLabels.has(`${s.row}${s.seatNumber}`)));
      $('selectedSeatLabel').textContent = selectedLabelFor(event);
      updateBuyButton();
      notify('error', t('toast.seatTaken').replace('{seat}', [...takenLabels].join(', ')), '');
    }
  } catch (error) { /* сеть моргнула — молча ждём следующий тик */ }
}

appInitialized = true;

/* =========================================================
   v17 FINAL ADD-ON — вставить в самый конец app.js
   (после строки appInitialized = true;)
   ========================================================= */

/* Страховка: closeAdminEventForm вызывается, но определения нет.
   Если в твоём реальном файле функция есть — этот блок пропустится. */
if (typeof closeAdminEventForm === 'undefined') {
  closeAdminEventForm = function () {
    const form = $('adminEventForm');
    if (!form) return;
    editingEventId = null;
    form.classList.add('hidden');
    form.reset();
    showAdminStatus('');
    setSeatConfigVisible(true);
  };
}

/* Строки календаря для обоих языков */
I18N.ru['search.anyDate'] = 'Любая дата';
I18N.en['search.anyDate'] = 'Any date';
I18N.ru['cal.today'] = 'Сегодня';
I18N.en['cal.today'] = 'Today';
I18N.ru['cal.clear'] = 'Очистить';
I18N.en['cal.clear'] = 'Clear';
I18N.ru['cal.clearDate'] = 'Очистить дату';
I18N.en['cal.clearDate'] = 'Clear date';

/* Кастомный календарь: точки на днях с событиями, «Сегодня»/«Очистить»,
   прошедшие дни неактивны (уважает initDateMin), дата не подставляется сама */
(function initDatePicker() {
  const input = $('dateFilter');
  const btn = $('dateBtn');
  const label = $('dateLabel');
  const clearBtn = $('dateClear');
  const cal = $('dateCalendar');
  if (!input || !btn || !label || !cal) return;

  let view = null;      // отображаемый месяц { y, m }
  let touched = false;  // пользователь сам выбирал/очищал дату?

  const DOW = () => Array.from({ length: 7 }, (_, i) =>
      new Date(2024, 0, 1 + i).toLocaleDateString(localeTag(), { weekday: 'short' }).replace('.', ''));
  const isoOf = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const parseIso = (iso) => { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d); };

  function fmtPicked(iso) {
    const d = parseIso(iso);
    const sameYear = d.getFullYear() === new Date().getFullYear();
    return d.toLocaleDateString(localeTag(), sameYear ? { day: 'numeric', month: 'short' } : { day: 'numeric', month: 'short', year: 'numeric' });
  }
  function syncLabel() {
    const v = input.value;
    btn.classList.toggle('empty', !v);
    label.textContent = v ? fmtPicked(v) : t('search.anyDate');
    if (clearBtn) clearBtn.classList.toggle('hidden', !v);
  }
  function eventDaySet() {
    const set = new Set();
    allEvents.forEach((e) => {
      const d = new Date(e.eventDate);
      if (e.eventDate && Number.isFinite(d.getTime())) set.add(isoOf(d));
    });
    return set;
  }

  function renderCal() {
    const selectedIso = input.value || '';
    const todayIso = isoOf(new Date());
    const minIso = input.min || '';
    if (!view) {
      const base = selectedIso ? parseIso(selectedIso) : new Date();
      view = { y: base.getFullYear(), m: base.getMonth() };
    }
    const evDays = eventDaySet();
    const y = view.y, m = view.m;
    const title = `${new Date(y, m, 1).toLocaleDateString(localeTag(), { month: 'long' })} ${y}`;
    const firstDow = (new Date(y, m, 1).getDay() + 6) % 7; // Пн = 0
    const daysIn = new Date(y, m + 1, 0).getDate();
    const prevIn = new Date(y, m, 0).getDate();
    const cells = [];
    for (let i = firstDow; i > 0; i--) {
      const d = new Date(y, m - 1, prevIn - i + 1);
      cells.push({ iso: isoOf(d), num: d.getDate(), other: true });
    }
    for (let i = 1; i <= daysIn; i++) cells.push({ iso: isoOf(new Date(y, m, i)), num: i, other: false });
    let nextDay = 1;
    while (cells.length % 7 !== 0) {
      const d = new Date(y, m + 1, nextDay++);
      cells.push({ iso: isoOf(d), num: d.getDate(), other: true });
    }
    cal.innerHTML = `
      <div class="cal-head">
        <strong class="cal-title">${escapeHtml(title)}</strong>
        <div class="cal-nav">
          <button type="button" data-nav="-1" aria-label="‹">‹</button>
          <button type="button" data-nav="1" aria-label="›">›</button>
        </div>
      </div>
      <div class="cal-grid">
        ${DOW().map((w) => `<span class="cal-dow">${escapeHtml(w)}</span>`).join('')}
        ${cells.map((c) => {
      const past = Boolean(minIso) && c.iso < minIso;
      const cls = ['cal-day', c.other ? 'other' : '', c.iso === todayIso ? 'today' : '', c.iso === selectedIso ? 'selected' : '', evDays.has(c.iso) ? 'has-events' : ''].filter(Boolean).join(' ');
      return `<button type="button" class="${cls}" data-iso="${c.iso}"${past ? ' disabled' : ''}>${c.num}</button>`;
    }).join('')}
      </div>
      <div class="cal-foot">
        <button type="button" class="cal-today">${escapeHtml(t('cal.today'))}</button>
        <button type="button" class="cal-clear">${escapeHtml(t('cal.clear'))}</button>
      </div>`;
  }

  function openCal() { renderCal(); cal.classList.remove('hidden'); btn.classList.add('open'); btn.setAttribute('aria-expanded', 'true'); }
  function closeCal() { cal.classList.add('hidden'); btn.classList.remove('open'); btn.setAttribute('aria-expanded', 'false'); }
  const toggleCal = () => (cal.classList.contains('hidden') ? openCal() : closeCal());

  function setDate(iso) {
    touched = true;
    input.value = iso;
    view = null;
    syncLabel();
    input.dispatchEvent(new Event('change', { bubbles: true }));
    closeCal();
  }
  function clearDate() {
    touched = true;
    input.value = '';
    syncLabel();
    input.dispatchEvent(new Event('change', { bubbles: true }));
    closeCal();
  }

  btn.addEventListener('click', toggleCal);
  if (clearBtn) clearBtn.addEventListener('click', clearDate);
  document.querySelector('label[for="dateFilter"]')?.addEventListener('click', (e) => { e.preventDefault(); toggleCal(); });

  cal.addEventListener('click', (e) => {
    const nav = e.target.closest('[data-nav]');
    if (nav) {
      const d = new Date(view.y, view.m + Number(nav.dataset.nav), 1);
      view = { y: d.getFullYear(), m: d.getMonth() };
      renderCal();
      return;
    }
    const dayBtn = e.target.closest('.cal-day');
    if (dayBtn && dayBtn.dataset.iso && !dayBtn.disabled) { setDate(dayBtn.dataset.iso); return; }
    if (e.target.closest('.cal-today')) { setDate(isoOf(new Date())); return; }
    if (e.target.closest('.cal-clear')) { clearDate(); return; }
  });

  // закрытие: клик мимо / Esc
  document.addEventListener('pointerdown', (e) => {
    if (!(e.target instanceof Element) || !e.target.closest('#dateField')) closeCal();
  }, true);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeCal(); });

  // анти-autofill: пока пользователь сам не выбрал дату — поле пустое
  ['DOMContentLoaded', 'load'].forEach((ev) => window.addEventListener(ev, () => {
    if (!touched) input.value = '';
    syncLabel();
  }));
  syncLabel();

  // перевязка текста при смене языка (applyLanguageNow меняет <html lang>)
  new MutationObserver(() => {
    syncLabel();
    if (!cal.classList.contains('hidden')) renderCal();
  }).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });

  if (clearBtn) clearBtn.setAttribute('aria-label', t('cal.clearDate'));
  input.value = '';
  syncLabel();
})();