<?php
// seed do moodle pro reengajamento, roda dentro do container pelo scripts/seed_moodle.sh
//   php seed.php [--reset-dates]
// configura webservice, usuário/papel/token, disciplina, 5 alunos e as tarefas T1..T5

define('CLI_SCRIPT', true);

require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/enrollib.php');
require_once($CFG->dirroot . '/course/lib.php');
require_once($CFG->dirroot . '/course/modlib.php');
require_once($CFG->dirroot . '/user/lib.php');
require_once($CFG->dirroot . '/mod/assign/locallib.php');
require_once($CFG->dirroot . '/webservice/lib.php');

[$options] = cli_get_params(['reset-dates' => false, 'help' => false], ['h' => 'help']);
if ($options['help']) {
    echo "Uso: php seed.php [--reset-dates]\n";
    echo "  --reset-dates  reposiciona os prazos de T1..T5 relativos a agora\n";
    exit(0);
}

const SERVICE_USERNAME = 'ws_reengajamento';
const SERVICE_ROLE = 'ws_reengajamento';
const SERVICE_SHORTNAME = 'reengajamento';
const COURSE_SHORTNAME = 'ES2-REENG';
const WS_FUNCTIONS = [
    'mod_assign_get_assignments',
    'core_enrol_get_enrolled_users',
    'mod_assign_get_submissions',
];
// conferido no código do moodle 5.3
const SERVICE_CAPABILITIES = [
    'webservice/rest:use',            // rest
    'moodle/course:view',             // ver a disciplina sem estar matriculado
    'mod/assign:view',                // mod_assign_get_assignments
    'mod/assign:viewgrades',          // mod_assign_get_submissions
    'moodle/course:viewparticipants', // core_enrol_get_enrolled_users
    'moodle/course:enrolreview',      // precisa pro onlyactive
    'moodle/user:viewdetails',        // detalhes dos usuários
    'moodle/site:viewuseridentity',   // email
];
const STUDENTS = [
    ['aluno1', 'Ana', 'Souza'],
    ['aluno2', 'Bruno', 'Lima'],
    ['aluno3', 'Carla', 'Mendes'],
    ['aluno4', 'Diego', 'Rocha'],
    ['aluno5', 'Elisa', 'Martins'],
];
// aluno que já enviou a t2
const T2_SUBMITTER = 'aluno1';

function out(string $msg): void {
    echo "[seed] {$msg}\n";
}

// prazos relativos a agora
function assignment_specs(int $now): array {
    return [
        'T1' => ['name' => 'T1 - Diagrama de casos de uso', 'duedate' => $now + 12 * HOURSECS],
        'T2' => ['name' => 'T2 - Modelo de domínio', 'duedate' => $now + 12 * HOURSECS],
        'T3' => ['name' => 'T3 - Plano de testes', 'duedate' => $now + 3 * DAYSECS],
        'T4' => ['name' => 'T4 - Requisitos (encerrada)', 'duedate' => $now - 2 * DAYSECS],
        'T5' => ['name' => 'T5 - Leitura complementar (sem prazo)', 'duedate' => 0],
    ];
}

// ---------------------------------------------------------------------------

\core\session\manager::set_user(get_admin());
$syscontext = context_system::instance();
$now = time();

// 1. webservices + rest
set_config('enablewebservices', 1);
$protocols = array_filter(explode(',', (string) get_config('core', 'webserviceprotocols')));
if (!in_array('rest', $protocols, true)) {
    $protocols[] = 'rest';
    set_config('webserviceprotocols', implode(',', $protocols));
}
// quem manda email é o notificacoes, o moodle não pode mandar nada (ex: recibo de envio)
set_config('noemailever', 1);
out('webservices habilitados (protocolo rest); e-mails do Moodle desligados');

// 2. papel e usuário de serviço
$roleid = $DB->get_field('role', 'id', ['shortname' => SERVICE_ROLE]);
if (!$roleid) {
    $roleid = create_role('Webservice Reengajamento', SERVICE_ROLE, 'Papel do usuário de serviço do reengajamento');
}
set_role_contextlevels($roleid, [CONTEXT_SYSTEM]);
foreach (SERVICE_CAPABILITIES as $cap) {
    assign_capability($cap, CAP_ALLOW, $roleid, $syscontext->id, true);
}
$syscontext->mark_dirty();

$wsuser = $DB->get_record('user', ['username' => SERVICE_USERNAME, 'deleted' => 0]);
if (!$wsuser) {
    $id = user_create_user([
        'username' => SERVICE_USERNAME,
        'auth' => 'manual',
        'password' => generate_password(20) . 'aA1!',
        'firstname' => 'Serviço',
        'lastname' => 'Reengajamento',
        'email' => 'ws_reengajamento@example.com',
        'confirmed' => 1,
        'mnethostid' => $CFG->mnet_localhost_id,
    ]);
    $wsuser = $DB->get_record('user', ['id' => $id], '*', MUST_EXIST);
}
role_assign($roleid, $wsuser->id, $syscontext->id);
out("usuário de serviço '" . SERVICE_USERNAME . "' (id {$wsuser->id}) com papel '" . SERVICE_ROLE . "'");

// 3. serviço externo, funções e token
$wsmanager = new webservice();
$service = $DB->get_record('external_services', ['shortname' => SERVICE_SHORTNAME]);
if (!$service) {
    $serviceid = $wsmanager->add_external_service((object) [
        'name' => 'Reengajamento de estudantes',
        'shortname' => SERVICE_SHORTNAME,
        'enabled' => 1,
        'restrictedusers' => 0,
        'downloadfiles' => 0,
        'uploadfiles' => 0,
    ]);
    $service = $DB->get_record('external_services', ['id' => $serviceid], '*', MUST_EXIST);
}
foreach (WS_FUNCTIONS as $fn) {
    if (!$wsmanager->service_function_exists($fn, $service->id)) {
        $wsmanager->add_external_function_to_service($fn, $service->id);
    }
}
$tokenrecord = $DB->get_record('external_tokens', [
    'userid' => $wsuser->id,
    'externalserviceid' => $service->id,
    'tokentype' => EXTERNAL_TOKEN_PERMANENT,
]);
$token = $tokenrecord
    ? $tokenrecord->token
    : \core_external\util::generate_token(EXTERNAL_TOKEN_PERMANENT, $service, $wsuser->id, $syscontext, 0, '', 'Reengajamento');
out("serviço externo '" . SERVICE_SHORTNAME . "' com " . count(WS_FUNCTIONS) . ' funções');

// 4. disciplina e alunos
$course = $DB->get_record('course', ['shortname' => COURSE_SHORTNAME]);
if (!$course) {
    $course = create_course((object) [
        'fullname' => 'Engenharia de Software II',
        'shortname' => COURSE_SHORTNAME,
        'category' => $DB->get_field_sql('SELECT MIN(id) FROM {course_categories}'),
        'format' => 'topics',
        'numsections' => 1,
        'visible' => 1,
    ]);
}
out("disciplina '{$course->fullname}' (id {$course->id})");

$studentroleid = $DB->get_field('role', 'id', ['shortname' => 'student'], MUST_EXIST);
$manual = enrol_get_plugin('manual');
$instance = $DB->get_record('enrol', ['courseid' => $course->id, 'enrol' => 'manual']);
if (!$instance) {
    $manual->add_default_instance($course);
    $instance = $DB->get_record('enrol', ['courseid' => $course->id, 'enrol' => 'manual'], '*', MUST_EXIST);
}
$students = [];
foreach (STUDENTS as [$username, $firstname, $lastname]) {
    $user = $DB->get_record('user', ['username' => $username, 'deleted' => 0]);
    if (!$user) {
        $id = user_create_user([
            'username' => $username,
            'auth' => 'manual',
            'password' => 'Aluno#2026',
            'firstname' => $firstname,
            'lastname' => $lastname,
            'email' => "{$username}@example.com",
            'confirmed' => 1,
            'mnethostid' => $CFG->mnet_localhost_id,
        ]);
        $user = $DB->get_record('user', ['id' => $id], '*', MUST_EXIST);
    }
    // enrol_user pode chamar de novo, só atualiza
    $manual->enrol_user($instance, $user->id, $studentroleid);
    $students[$username] = $user;
}
out(count($students) . ' alunos matriculados como estudantes');

// 5. tarefas T1..T5
$assignmoduleid = $DB->get_field('modules', 'id', ['name' => 'assign'], MUST_EXIST);
$cms = [];
foreach (assignment_specs($now) as $key => $spec) {
    $idnumber = 'reeng-' . $key;
    $cm = $DB->get_record('course_modules', ['course' => $course->id, 'idnumber' => $idnumber, 'module' => $assignmoduleid]);
    if (!$cm) {
        $moduleinfo = add_moduleinfo((object) [
            'modulename' => 'assign',
            'module' => $assignmoduleid,
            'course' => $course->id,
            'section' => 0,
            'visible' => 1,
            'visibleoncoursepage' => 1,
            'cmidnumber' => $idnumber,
            'groupmode' => 0,
            'groupingid' => 0,
            'availability' => null,
            'completion' => 0,
            'name' => $spec['name'],
            'intro' => "Tarefa de teste {$key} do serviço de reengajamento.",
            'introformat' => FORMAT_HTML,
            'alwaysshowdescription' => 1,
            'submissiondrafts' => 0, // sem rascunho, já fica submitted
            'requiresubmissionstatement' => 0,
            'sendnotifications' => 0,
            'sendlatenotifications' => 0,
            'sendstudentnotifications' => 0,
            'duedate' => $spec['duedate'],
            'allowsubmissionsfromdate' => 0,
            'cutoffdate' => 0,
            'gradingduedate' => 0,
            'grade' => 100,
            'teamsubmission' => 0,
            'requireallteammemberssubmit' => 0,
            'teamsubmissiongroupingid' => 0,
            'blindmarking' => 0,
            'attemptreopenmethod' => 'untilpass',
            'maxattempts' => 1,
            'markingworkflow' => 0,
            'markingallocation' => 0,
            'markercount' => 0,
            'optionalmarkercount' => 0,
            'markinganonymous' => 0,
            'activityformat' => 0,
            'timelimit' => 0,
            'submissionattachments' => 0,
            'assignsubmission_onlinetext_enabled' => 1,
            'assignsubmission_file_enabled' => 0,
            'assignfeedback_comments_enabled' => 0,
        ], $course);
        $cm = $DB->get_record('course_modules', ['id' => $moduleinfo->coursemodule], '*', MUST_EXIST);
        out("{$key} criada (cmid {$cm->id}, prazo " . ($spec['duedate'] ? userdate($spec['duedate']) : 'nenhum') . ')');
    } else if ($options['reset-dates']) {
        $DB->set_field('assign', 'duedate', $spec['duedate'], ['id' => $cm->instance]);
        out("{$key} prazo reposicionado (cmid {$cm->id}, prazo " . ($spec['duedate'] ? userdate($spec['duedate']) : 'nenhum') . ')');
    } else {
        out("{$key} já existe (cmid {$cm->id})");
    }
    $cms[$key] = $cm;
}
if ($options['reset-dates']) {
    rebuild_course_cache($course->id, true);
    foreach ($cms as $cm) {
        [, $cminfo] = get_course_and_cm_from_cmid($cm->id, 'assign');
        (new assign(context_module::instance($cm->id), $cminfo, $course))->update_calendar($cm->id);
    }
}

// envio do aluno1 na t2
[, $t2cm] = get_course_and_cm_from_cmid($cms['T2']->id, 'assign');
$t2 = new assign(context_module::instance($t2cm->id), $t2cm, $course);
$submitter = $students[T2_SUBMITTER];
$existing = $t2->get_user_submission($submitter->id, false);
if (!$existing || $existing->status !== ASSIGN_SUBMISSION_STATUS_SUBMITTED) {
    \core\session\manager::set_user($submitter);
    $notices = [];
    $t2->save_submission((object) [
        'userid' => $submitter->id,
        'onlinetext_editor' => [
            'itemid' => file_get_unused_draft_itemid(),
            'text' => '<p>Modelo de domínio entregue.</p>',
            'format' => FORMAT_HTML,
        ],
    ], $notices);
    \core\session\manager::set_user(get_admin());
    $status = $t2->get_user_submission($submitter->id, false)->status ?? 'none';
    if ($status !== ASSIGN_SUBMISSION_STATUS_SUBMITTED) {
        cli_error('falha ao registrar a submissão da T2: status=' . $status . ' ' . implode('; ', $notices));
    }
}
out("submissão 'submitted' de " . T2_SUBMITTER . ' na T2');

// o seed_moodle.sh lê essas linhas, não mudar o formato
echo "MOODLE_TOKEN={$token}\n";
echo "MOODLE_COURSE_ID={$course->id}\n";
